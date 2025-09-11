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
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
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
    private val paymentOrderModificationPort: PaymentOrderModificationPort,
    private val clock: Clock,
) : ProcessPspResultUseCase {

    private val paymentOrderFactory: PaymentOrderFactory = PaymentOrderFactory()
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object const

    val MAX_RETRIES: Int = 5

    override fun processPspResult(
        event: PaymentOrderEvent,
        pspStatus: PaymentOrderStatus
    ) {
        val totalStart = System.currentTimeMillis()
        val order = paymentOrderFactory.fromEvent(event)

        try {
            when {
                pspStatus == PaymentOrderStatus.SUCCESSFUL_FINAL -> {
                    val dbStart = System.currentTimeMillis()
                    handleSuccessfulPayment(order)
                    logger.info(
                        "TIMING:  processPspResult {} (DB/write+publish) took {} ms for {}",
                        pspStatus.name, (System.currentTimeMillis() - dbStart), order.paymentOrderId
                    )
                }

                PSPStatusMapper.requiresRetryPayment(pspStatus) -> {
                    val dbStart = System.currentTimeMillis()
                    handleRetryEvent(order = order, reason = pspStatus.name, lastError = null)
                    logger.info(
                        "TIMING:  processPspResult {} (DB/write+schedule-retry) took {} ms for {}",
                        pspStatus.name, (System.currentTimeMillis() - dbStart), order.paymentOrderId
                    )
                }

                PSPStatusMapper.requiresStatusCheck(pspStatus) -> {
                    val dbStart = System.currentTimeMillis()
                    handlePaymentStatusCheckEvent(order, reason = pspStatus.name, lastError = null)
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

    private fun handleRetryEvent(order: PaymentOrder, reason: String?, lastError: String?) {
        val retriesSoFar = order.retryCount        // from the event; attempt i (0 for initial)

        if (retriesSoFar >= MAX_RETRIES) {
            retryQueuePort.resetRetryCounter(order.paymentOrderId)
            handleNonRetryableFailEvent(order, reason)
            return
        }

        // Persist the failure → this increments to retriesSoFar + 1
        val persisted = paymentOrderModificationPort.markFailedForRetry(order, reason, lastError)

        val nextAttempt = persisted.retryCount     // == retriesSoFar + 1
        val backoffMs = computeEqualJitterBackoff(nextAttempt)

        logRetrySchedule(persisted, nextAttempt, System.currentTimeMillis() + backoffMs, reason, lastError)
        retryQueuePort.scheduleRetry(
            paymentOrder = persisted,
            retryReason = reason,
            backOffMillis = backoffMs,
            lastErrorMessage = lastError
        )
    }

    private fun handlePaymentStatusCheckEvent(order: PaymentOrder, reason: String?, lastError: String?) {
        // markPendingAndScheduleStatusCheck already checks terminal/non-terminal internally
        paymentOrderModificationPort.markPendingAndScheduleStatusCheck(order, reason, lastError)
    }

    private fun handleSuccessfulPayment(order: PaymentOrder) {
        val persisted = paymentOrderModificationPort.markPaid(order)   // ← persisted
        eventPublisher.publishSync(
            eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
            aggregateId = persisted.publicPaymentOrderId,
            data = PaymentOrderDomainEventMapper.toPaymentOrderSuccededEvent(persisted),
            parentEventId = LogContext.getEventId(),
            traceId = LogContext.getTraceId()
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
        val updated = paymentOrderModificationPort.markFinalFailed(order, reason)
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