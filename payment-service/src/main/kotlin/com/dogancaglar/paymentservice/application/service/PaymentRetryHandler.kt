package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusScheduled
import com.dogancaglar.paymentservice.domain.event.ScheduledPaymentOrderStatusRequest
import com.dogancaglar.paymentservice.domain.event.mapper.toRetryEvent
import com.dogancaglar.paymentservice.domain.event.toPaymentOrderStatusScheduled
import com.dogancaglar.paymentservice.domain.event.toSchedulePaymentOrderStatusEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class PaymentRetryHandler(
    private val paymentOrderRepository: PaymentOrderRepository,
    @Qualifier("paymentRetryPaymentAdapter")
    private val paymentRetryPaymentAdapter: RetryQueuePort<PaymentOrderRetryRequested>,
    @Qualifier("paymentRetryStatusAdapter")
    private val paymentRetryStatusAdapter: RetryQueuePort<PaymentOrderStatusScheduled>,
    private val paymentEventPublisher: PaymentEventPublisher
) : RetryHandler {

    private val logger = LoggerFactory.getLogger(javaClass)


    override fun retryPayment(parentEnvelope: EventEnvelope<*>, order: PaymentOrder, reason: String?, error: String?) {
        val failed = order.markAsFailed()
            .incrementRetry()
            .withRetryReason(reason)
            .withLastError(error)
            .updatedAt(LocalDateTime.now())

        paymentOrderRepository.save(failed)

        if (failed.retryCount < MAX_RETRY) {
            paymentRetryPaymentAdapter.scheduleRetry(failed)
            paymentEventPublisher.publish(data =failed.toRetryEvent(reason,error),
                event = EventMetadatas.PaymentOrderRetryRequestedMetadata, aggregateId = failed.paymentOrderId
            , parentEnvelope = parentEnvelope)//publish schedule
            logger.warn("🔁 Retry scheduled: order=${failed.paymentOrderId}, retry=${failed.retryCount}, reason=$reason")
        } else {
            logger.error("🚫 Max retries exceeded for order=${failed.paymentOrderId}")
        }
    }

    override fun scheduleStatusCheck(envelope: EventEnvelope<*> ,order: PaymentOrder, reason: String?, error: String?) {
        val failed = order.incrementRetry()
            .withRetryReason(reason)
            .withLastError(error)
            .markAsPending()

        paymentOrderRepository.save(failed)
        paymentRetryStatusAdapter.scheduleRetry(failed)
        paymentEventPublisher.publish( event= EventMetadatas.PaymentOrderStatusCheckScheduledMetadata,
            aggregateId = failed.paymentOrderId,data =failed.toPaymentOrderStatusScheduled(reason,error))//paymentorder scheduled
        logger.warn("📅 Status check scheduled: order=${failed.paymentOrderId}, retry=${failed.retryCount}")
    }

    override fun finalizeAsNonRetryable(order: PaymentOrder, status: PaymentOrderStatus) {
        val finalized = order.markAsFinalizedFailed()
            .withRetryReason("Non-retryable PSP status: $status")
            .updatedAt(LocalDateTime.now())

        paymentOrderRepository.save(finalized)
        logger.warn("❌ Finalized as non-retryable: order=${order.paymentOrderId}, status=$status")
    }

    companion object {
        private const val MAX_RETRY = 5
    }
}