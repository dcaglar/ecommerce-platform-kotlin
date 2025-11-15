package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.metadata.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.util.PSPCaptureStatusMapper
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

open class ProcessPaymentService(
    @param:Qualifier("syncPaymentEventPublisher")
    private val eventPublisher: EventPublisherPort,
    private val retryQueuePort: RetryQueuePort<PaymentOrderCaptureCommand>,
    private val paymentOrderModificationPort: PaymentOrderModificationPort,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
    private val clock: Clock
) : ProcessPspResultUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_RETRIES = 5
    }

    override fun processPspResult(event: PaymentOrderEvent, pspStatus: PaymentOrderStatus) {
        val totalStart = System.currentTimeMillis()
        val order = paymentOrderDomainEventMapper.fromEvent(event)

        try {
            when {
                PSPCaptureStatusMapper.requiresRetry(pspStatus) -> {
                    val dbStart = System.currentTimeMillis()
                    handleRetryEvent(order = order)
                    logger.info(
                        "TIMING: processPspResult {} (DB/write+schedule-retry) took {} ms for {}",
                        pspStatus.name, (System.currentTimeMillis() - dbStart), order.paymentOrderId
                    )
                }
                pspStatus == PaymentOrderStatus.CAPTURED -> {
                    val dbStart = System.currentTimeMillis()
                    handleCapturedPaymentOrder(order)
                    logger.info(
                        "TIMING: processPspResult {} (DB/write+publish) took {} ms for {}",
                        pspStatus.name, (System.currentTimeMillis() - dbStart), order.paymentOrderId
                    )
                }

                pspStatus == PaymentOrderStatus.CAPTURE_FAILED ->  {
                    val dbStart = System.currentTimeMillis()
                    handleNonRetryableFailEvent(order)
                    logger.info(
                        "TIMING: processPspResult {} (DB/write+publish) took {} ms for {}",
                        pspStatus.name, (System.currentTimeMillis() - dbStart), order.paymentOrderId
                    )
                }

                pspStatus == PaymentOrderStatus.REFUNDED -> {
                    throw UnsupportedOperationException("Not supported")
                }

                pspStatus == PaymentOrderStatus.REFUND_FAILED -> {
                   throw UnsupportedOperationException("Not supported")
                }
                else -> {
                    throw UnsupportedOperationException("Not supported")
                }
            }
        } finally {
            logger.info(
                "TIMING: processPspResult (Total) took {} ms for {}",
                (System.currentTimeMillis() - totalStart), order.paymentOrderId
            )
        }
    }

    private fun handleRetryEvent(order: PaymentOrder) {
        val retriesSoFar = order.retryCount

        if (retriesSoFar >= MAX_RETRIES) {
            retryQueuePort.resetRetryCounter(order.paymentOrderId)
            handleNonRetryableFailEvent(order)
            return
        }

        val persisted = paymentOrderModificationPort.markAsCapturePending(order)
        val nextAttempt = persisted.retryCount
        val backoffMs = computeEqualJitterBackoff(nextAttempt)

        logRetrySchedule(persisted, nextAttempt, System.currentTimeMillis() + backoffMs)

        retryQueuePort.scheduleRetry(
            paymentOrder = persisted,
            backOffMillis = backoffMs,
        )
    }


    private fun handleCapturedPaymentOrder(order: PaymentOrder) {
        val persisted = paymentOrderModificationPort.markAsCaptured(order)
        val succeededEvent = paymentOrderDomainEventMapper.toPaymentOrderSucceeded(persisted)

        eventPublisher.publishSync(
            eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
            aggregateId = persisted.paymentOrderId.value.toString(),
            data = succeededEvent,
            parentEventId = LogContext.getEventId(),
            traceId = LogContext.getTraceId()
        )
    }

    private fun handleNonRetryableFailEvent(order: PaymentOrder) {
        val updated = paymentOrderModificationPort.markAsCaptureFailed(order)
        val paymentOrderFailed = paymentOrderDomainEventMapper.toPaymentOrderFailed(updated)

        eventPublisher.publishSync(
            eventMetaData = EventMetadatas.PaymentOrderFailedMetadata,
            aggregateId = updated.paymentOrderId.value.toString(),
            data = paymentOrderFailed,
            parentEventId = LogContext.getEventId(),
            traceId = LogContext.getTraceId()
        )
    }

    private fun logRetrySchedule(
        order: PaymentOrder,
        nextRetryCount: Int,
        scheduledAt: Long,
    ) {
        val scheduledLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAt), clock.zone)
        val formattedLocal = scheduledLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        val scheduledUtc = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAt), ZoneId.of("UTC"))
        val formattedUtc = scheduledUtc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

        logger.info(
            "Scheduling retry for paymentOrderId={} [attempt {}/{}] due at {} (local, {}) / {} (UTC)",
            order.paymentOrderId.toPublicPaymentOrderId(), nextRetryCount, MAX_RETRIES,
            formattedLocal, clock.zone, formattedUtc,
        )
    }


    fun mapEventToDomain(event: PaymentOrderEvent): PaymentOrder =
        paymentOrderDomainEventMapper.fromEvent(event)

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