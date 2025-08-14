package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.util.PSPStatusMapper
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.util.PaymentOrderFactory
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderStatePort
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
    private val eventPublisher: EventPublisherPort,
    private val retryQueuePort: RetryQueuePort<PaymentOrderPspCallRequested>,
    private val pspResultCache: PspResultCachePort,
    private val paymentOrderStatePort: PaymentOrderStatePort,
    private val clock: Clock,
) : ProcessPspResultUseCase {

    private val paymentOrderFactory: PaymentOrderFactory = PaymentOrderFactory()
    private val logger = LoggerFactory.getLogger(javaClass)

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
                    val dbStart = System.currentTimeMillis()
                    handleSuccessfulPayment(order)
                    logger.info(
                        "TIMING:  processPspResult {} (DB/write+publish) took {} ms for {}",
                        pspStatus.name, (System.currentTimeMillis() - dbStart), order.paymentOrderId
                    )
                }

                PSPStatusMapper.requiresRetryPayment(pspStatus) -> {
                    val dbStart = System.currentTimeMillis()
                    handleRetryEvent(order, reason = pspStatus.name)
                    logger.info(
                        "TIMING:  processPspResult {} (DB/write+schedule-retry) took {} ms for {}",
                        pspStatus.name, (System.currentTimeMillis() - dbStart), order.paymentOrderId
                    )
                }

                PSPStatusMapper.requiresStatusCheck(pspStatus) -> {
                    val dbStart = System.currentTimeMillis()
                    handlePaymentStatusCheckEvent(order, reason = pspStatus.name)
                    logger.info(
                        "TIMING:  processPspResult {} (DB/write+STATUSDB/write) took {} ms for {}",
                        pspStatus.name, (System.currentTimeMillis() - dbStart), order.paymentOrderId
                    )
                }

                else -> {
                    handleNonRetryableFailEvent(order, reason = pspStatus.name)
                }
            }
        } finally {
            logger.info(
                "TIMING: processPspResult (Total) took {} ms for {}",
                (System.currentTimeMillis() - totalStart), order.paymentOrderId
            )
        }
    }

    private fun handleSuccessfulPayment(order: PaymentOrder) {
        logger.info("Payment Order is succesfull.")
        val updatedOrder = paymentOrderStatePort.markPaid(order)
        eventPublisher.publishSync(
            eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
            aggregateId = updatedOrder.publicPaymentOrderId,
            data = PaymentOrderDomainEventMapper.toPaymentOrderSuccededEvent(updatedOrder),
            parentEventId = LogContext.getEventId(),
            traceId = LogContext.getTraceId()
        )
    }

    private fun handlePaymentStatusCheckEvent(
        order: PaymentOrder,
        reason: String? = null,
        lastError: String? = null
    ) {
        logger.info("Payment order requires status check; marking as pending.")
        paymentOrderStatePort.markPendingAndScheduleStatusCheck(order, reason, lastError)
    }

    private fun handleRetryEvent(
        order: PaymentOrder,
        reason: String? = null,
        lastError: String? = null
    ) {
        pspResultCache.remove(order.paymentOrderId)

        // 1) Persist current failure & increment retryCount in DB
        val updated = paymentOrderStatePort.markFailedForRetry(order, reason, lastError)
        val nextAttempt = updated.retryCount

        // 2) Max attempts guard
        if (nextAttempt > MAX_RETRIES) {
            retryQueuePort.resetRetryCounter(updated.paymentOrderId) // optional housekeeping
            handleNonRetryableFailEvent(updated, reason)
            return
        }

        // 3) Backoff
        val backoffMs = computeEqualJitterBackoff(attempt = nextAttempt)
        val scheduledAt = System.currentTimeMillis() + backoffMs

        LogContext.withRetryFields(
            retryCount = nextAttempt,
            retryReason = reason,
            lastErrorMessage = lastError,
            backOffInMillis = backoffMs
        ) {
            logRetrySchedule(updated, nextAttempt, scheduledAt, reason, lastError)
        }

        // 4) Enqueue PSP_CALL_REQUESTED (attempt = DB retryCount)
        retryQueuePort.scheduleRetry(
            paymentOrder = updated,
            retryReason = reason,
            backOffMillis = backoffMs,
            lastErrorMessage = lastError
        )
    }

    private fun logRetrySchedule(
        order: PaymentOrder,
        nextRetryCount: Int,
        scheduledAt: Long,
        reason: String?,
        lastError: String?
    ) {
        val scheduledLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAt), clock.zone)
        val formattedLocal = scheduledLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        val scheduledUtc = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAt), ZoneId.of("UTC"))
        val formattedUtc = scheduledUtc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        logger.info(
            "Scheduling retry for paymentOrderId={} [attempt {}/{}] due at {} (local, {}) / {} (UTC). reason='{}', lastError='{}'",
            order.publicPaymentOrderId, nextRetryCount, MAX_RETRIES,
            formattedLocal, clock.zone, formattedUtc,
            reason ?: "", lastError ?: ""
        )
    }

    private fun handleNonRetryableFailEvent(
        order: PaymentOrder,
        reason: String? = null
    ): PaymentOrder {
        val updated = paymentOrderStatePort.markFinalFailed(order, reason)
        logger.info(
            "PaymentOrder {} marked FINAL_FAILED. Reason='{}'",
            updated.publicPaymentOrderId, (reason ?: updated.retryReason ?: "N/A")
        )
        return updated
    }

    fun mapEventToDomain(event: PaymentOrderEvent): PaymentOrder =
        paymentOrderFactory.fromEvent(event)

    fun computeEqualJitterBackoff(
        attempt: Int,
        minDelayMs: Long = 2_000L,
        maxDelayMs: Long = 60_000L,
        random: Random = Random.Default
    ): Long {
        require(attempt >= 1) { "Attempt must be >= 1" }
        val exp = (minDelayMs * 2.0.pow(attempt - 1)).toLong()
        val capped = min(exp, maxDelayMs)
        val half = capped / 2
        return half + random.nextLong(half + 1) // [half, capped]
    }
}