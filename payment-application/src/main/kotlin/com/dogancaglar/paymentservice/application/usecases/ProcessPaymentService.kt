package com.dogancaglar.paymentservice.domain.config

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatusCheck
import com.dogancaglar.paymentservice.domain.util.PSPStatusMapper
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.util.PaymentOrderFactory
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderStatusCheckRepository
import com.dogancaglar.paymentservice.ports.outbound.PspResultCachePort
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

open class ProcessPaymentService(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val eventPublisher: EventPublisherPort,
    private val retryQueuePort: RetryQueuePort<PaymentOrderRetryRequested>,
    private val pspResultCache: PspResultCachePort,
    private val statusCheckRepo: PaymentOrderStatusCheckRepository,
    private val clock: Clock
) : ProcessPspResultUseCase {


    private val paymentOrderFactory: PaymentOrderFactory = PaymentOrderFactory()
    val logger = LoggerFactory.getLogger(javaClass)

    companion object const

    val MAX_RETRIES: Int = 10
    override fun processPspResult(
        event: PaymentOrderEvent,
        pspStatus: PaymentOrderStatus
    ) {
        val totalStart = System.currentTimeMillis()
        val order = paymentOrderFactory.fromEvent(event)

        try {
            when {
                pspStatus == PaymentOrderStatus.SUCCESSFUL -> {
                    //keeep cache
                    val dbStart = System.currentTimeMillis()
                    handleSuccessfulPayment(order = order)
                    val dbEnd = System.currentTimeMillis()
                    logger.info("TIMING: processPspResult (DB/write) took ${dbEnd - dbStart} ms for ${order.paymentOrderId}")
                }

                PSPStatusMapper.requiresRetryPayment(pspStatus) -> {
                    handleRetryEvent(order = order, reason = pspStatus.name)
                }

                PSPStatusMapper.requiresStatusCheck(pspStatus) -> {
                    handlePaymentStatusCheckEvent(order)
                }

                else -> {
                    handleNonRetryableFailEvent(order)
                }
            }
        } finally {
            val totalEnd = System.currentTimeMillis()
            logger.info("TIMING: processPspResult (Total) took ${totalEnd - totalStart} ms for ${order.paymentOrderId}")
        }
    }


    private fun handleSuccessfulPayment(order: PaymentOrder) {
        logger.info("Payment Order is succesfull.")
        val updatedOrder = order.markAsPaid().withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderRepository.save(updatedOrder)
        // Publish the success event synchronously to ensure it is processed immediately
        eventPublisher.publishSync(
            eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
            aggregateId = updatedOrder.publicPaymentOrderId,
            data = PaymentOrderDomainEventMapper.toPaymentOrderSuccededEvent(updatedOrder),
            parentEventId = LogContext.getEventId(),
            traceId = LogContext.getTraceId()
        )
    }

    private fun handlePaymentStatusCheckEvent(
        order: PaymentOrder, reason: String? = null, lastError: String? = null
    ) {
        logger.info("Patment order failed to be processed, marking as pending for retry.")
        val updated = order.markAsPending().incrementRetry().withRetryReason(reason).withLastError(lastError)
            .withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderRepository.save(updated)

        statusCheckRepo.save(
            PaymentOrderStatusCheck.Companion.createNew(
                updated.paymentOrderId.value, LocalDateTime.now(clock).plusMinutes(30)
            )
        )
    }


    private fun handleRetryEvent(
        order: PaymentOrder, reason: String? = null, lastError: String? = null
    ) {
        pspResultCache.remove(order.paymentOrderId);
        logger.info(
            "Handling retry for  paymentOrderId=${order.paymentOrderId} with reason $reason and lastError $lastError",
        )
        val retryCount = retryQueuePort.getRetryCount(order.paymentOrderId)
        val nextRetryCount = retryCount + 1
        val updated = order.markAsFailed().withRetryReason(reason).withLastError(lastError)
            .withUpdatedAt(LocalDateTime.now(clock))

        if (nextRetryCount < MAX_RETRIES) {
            val backOffExpMillis = computeEqualJitterBackoff(attempt = nextRetryCount)
            val scheduledAt = System.currentTimeMillis().plus(backOffExpMillis)
            LogContext.withRetryFields(
                retryCount = nextRetryCount,
                retryReason = reason,
                lastErrorMessage = lastError,
                backOffInMillis = backOffExpMillis
            ) {
                logRetrySchedule(order, nextRetryCount, scheduledAt, reason, lastError)
            }
            retryQueuePort.scheduleRetry(order, retryReason = reason, backOffMillis = backOffExpMillis)
        } else {
            logger.warn(
                "[RETRY-FAILURE] paymentOrderId={} has reached the maximum retry attempts ({}/{}). Marking as permanently FAILED. LastError='{}', RetryReason='{}'",
                order.publicPaymentOrderId,
                nextRetryCount,
                MAX_RETRIES,
                lastError ?: "-",
                reason ?: "-"
            )
            // ✅ Reset the retry counter after exceeding max retries
            retryQueuePort.resetRetryCounter(order.paymentOrderId)
            //finalize the order
            handleNonRetryableFailEvent(updated)

        }
    }

    private fun logRetrySchedule(
        order: PaymentOrder, nextRetryCount: Int, scheduledAt: Long, reason: String?, lastError: String?
    ) {
        val scheduledLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAt), clock.zone)
        val formattedLocal = scheduledLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        val scheduledUtc = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAt), ZoneId.of("UTC"))
        val formattedUtc = scheduledUtc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        logger.info(
            "Scheduling retry for paymentOrderId={} [attempt {}/{}] due at {} (local, {}) / {} (UTC). Reason='{}', LastError='{}'",
            order.publicPaymentOrderId,
            nextRetryCount,
            MAX_RETRIES,
            formattedLocal,          // Local time
            clock.zone,              // What is 'local'
            formattedUtc,            // UTC time
            reason ?: "",
            lastError ?: ""
        )
    }

    private fun handleNonRetryableFailEvent(
        order: PaymentOrder, reason: String? = null
    ): PaymentOrder {
        val updated = order.markAsFinalizedFailed().withRetryReason(reason).withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderRepository.save(updated)
        logger.info(
            "PaymentOrder {} marked as permanently FAILED. Reason='{}'",
            updated.publicPaymentOrderId,
            (reason ?: updated.retryReason ?: "N/A")
        )
        return updated
    }


    fun mapEventToDomain(event: PaymentOrderEvent): PaymentOrder {
        return paymentOrderFactory.fromEvent(event)
    }


    /**
     * Calculates backoff delay using AWS "Equal Jitter" strategy.
     * Each retry waits at least half of the exponential backoff, up to maxDelay.
     *
     * @param attempt      1-based retry attempt number (first retry = 1)
     * @param minDelayMs   Minimum delay (e.g., 2000L for 2 seconds)
     * @param maxDelayMs   Maximum delay (e.g., 300000L for 5 minutes)
     * @param random       Random instance (for testability)
     * @return             Milliseconds to wait before next retry
     */
    fun computeEqualJitterBackoff(
        attempt: Int,
        minDelayMs: Long = 2_000L,
        maxDelayMs: Long = 60_000L,
        random: Random = Random.Default
    ): Long {
        require(attempt >= 1) { "Attempt must be >= 1" }
        val exp = minDelayMs * 2.0.pow(attempt - 1).toLong()
        val capped = min(exp, maxDelayMs)
        val half = capped / 2
        return half + random.nextLong(half + 1) // [half, capped]
    }

}