package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.util.PSPCaptureStatusMapper
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import org.slf4j.LoggerFactory
import com.dogancaglar.common.time.Utc
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
) : ProcessPspResultUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_RETRIES = 5
    }

    override fun processPspResult(event: PaymentOrderPspResultUpdated, order: PaymentOrder) {
        val start = System.currentTimeMillis()
        val pspStatus = PaymentOrderStatus.valueOf(event.pspStatus)
        when {
            PSPCaptureStatusMapper.requiresRetry(pspStatus) -> handleRetry(order)
            pspStatus == PaymentOrderStatus.CAPTURED -> handleCaptured(order)
            pspStatus == PaymentOrderStatus.CAPTURE_FAILED -> handleFailed(order)
            else -> logger.warn("⚠️ Unhandled PSP status={} for {}", pspStatus, order.paymentOrderId)
        }

        logger.info("⏱ processPspResult total={}ms poId={}", System.currentTimeMillis() - start, order.paymentOrderId)
    }

    private fun handleRetry(order: PaymentOrder) {
        if (order.retryCount >= MAX_RETRIES) {
            logger.warn("⚠️ Max retries reached for {}", order.paymentOrderId)
            handleFailed(order)
            return
        }
        // Use atomic method to transition to PENDING_CAPTURE and increment retry count
        // This avoids domain invariant violations:
        // - CAPTURE_REQUESTED requires retryCount == 0
        // - PENDING_CAPTURE requires retryCount > 0
        // We can't do these operations separately without violating invariants
        val draft = order.markCapturePendingAndIncrementRetry()
        val persisted = paymentOrderModificationPort.updateReturningIdempotent(draft)
        val nextAttempt = persisted.retryCount
        val backoffMs = computeEqualJitterBackoff(nextAttempt)
        logRetrySchedule(persisted, nextAttempt, System.currentTimeMillis() + backoffMs)
        retryQueuePort.scheduleRetry(persisted, backoffMs)
    }

    private fun handleCaptured(order: PaymentOrder) {
        val draft = order.markAsCaptured()
        val persisted = paymentOrderModificationPort.updateReturningIdempotent(draft)
        val evt = paymentOrderDomainEventMapper.toPaymentOrderFinalized(persisted, Utc.nowLocalDateTime(),
            PaymentOrderStatus.CAPTURED)
        eventPublisher.publishSync(
            EventLogContext.getAggregateId()!!,
            evt,
            EventLogContext.getTraceId(),
            parentEventId = EventLogContext.getEventId()
        )
        logger.info("✅ Capture succeeded for {}", order.paymentOrderId)
    }

    private fun handleFailed(order: PaymentOrder) {
        val draft = order.markCaptureDeclined()
        val persisted = paymentOrderModificationPort.updateReturningIdempotent(draft)
        val evt = paymentOrderDomainEventMapper.toPaymentOrderFinalized(persisted, Utc.nowLocalDateTime(),
            PaymentOrderStatus.CAPTURE_FAILED)
        eventPublisher.publishSync(
            EventLogContext.getAggregateId()!!,
            evt,
            EventLogContext.getTraceId(),
            parentEventId = EventLogContext.getEventId()
        )
        logger.warn("❌ Capture failed for {}", order.paymentOrderId)
    }
    private fun logRetrySchedule(
        order: PaymentOrder,
        nextRetryCount: Int,
        scheduledAt: Long,
    ) {
        val scheduledInstant = Instant.ofEpochMilli(scheduledAt)
        val amsterdamZone = ZoneId.of("Europe/Amsterdam")
        val scheduledLocal = LocalDateTime.ofInstant(scheduledInstant, amsterdamZone)
        val formattedLocal = scheduledLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

        logger.info(
            "Scheduling retry for paymentOrderId=${order.paymentOrderId.toPublicPaymentOrderId()} " +
                    "[attempt $nextRetryCount/$MAX_RETRIES] due at $formattedLocal (Amsterdam time)"
        )
            }


    private fun computeEqualJitterBackoff(attempt: Int, minDelayMs: Long = 2000L, maxDelayMs: Long = 60000L): Long {
        val exp = (minDelayMs * 2.0.pow((attempt - 1).coerceAtLeast(0))).toLong()
        val capped = min(exp, maxDelayMs)
        return capped / 2 + Random.nextLong(capped / 2 + 1)
    }
}