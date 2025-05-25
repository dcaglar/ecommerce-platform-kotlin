package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.delayqueue.RequestStatus
import com.dogancaglar.paymentservice.adapter.delayqueue.ScheduledPaymentOrderStatusService
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.DuePaymentOrderStatusCheck
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.domain.event.ScheduledPaymentOrderStatusRequest
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.EventPublisherPort
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.dogancaglar.paymentservice.psp.PSPClient
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
//ScheduledPaymentStatusCheckExecutor will listen PaymentOrderStatusScheduled and perform actual status check
@Component
class  ScheduledPaymentStatusCheckExecutor(
    private val paymentOrderRepository: PaymentOrderOutboundPort,
    private val pspClient: PSPClient,
    private val paymentEventPublisher: EventPublisherPort,
    @Qualifier("paymentRetryStatusAdapter")
    val paymentRetryStatusAdapter: RetryQueuePort<ScheduledPaymentOrderStatusRequest>,
    val scheduledPaymentOrderStatusService : ScheduledPaymentOrderStatusService
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun handle(record: ConsumerRecord<String, EventEnvelope<DuePaymentOrderStatusCheck>>) {
        // TODO BU METODU ALACAK PSP CALSTRACAK O KADAR
        val envelope = record.value()
        LogContext.with(envelope) {
            MDC.put(LogFields.TOPIC_NAME, record.topic())
            MDC.put(LogFields.CONSUMER_GROUP, record.topic())
            MDC.put(LogFields.PAYMENT_ORDER_ID, envelope.data.paymentOrderId)
            val paymentOrderStatusCheck = envelope.data
            val paymentOrder = paymentOrderStatusCheck.toDomain()
            try {
                logger.info("Performing PSP status check wtih retry=${paymentOrder.retryCount}")
                val response: PaymentOrderStatus = safePspCall(paymentOrder,)
                logger.info("Updading PSP status check for payment=${paymentOrder.paymentOrderId}")
                scheduledPaymentOrderStatusService.updateStatusToExecuted(paymentOrder.paymentOrderId, RequestStatus.EXECUTED)
                when (response) {
                    PaymentOrderStatus.SUCCESSFUL -> {
                        val paidOrder = paymentOrder.markAsPaid().updatedAt(LocalDateTime.now())
                        paymentOrderRepository.save(paidOrder)

                    }

                    PaymentOrderStatus.PENDING,
                    PaymentOrderStatus.CAPTURE_PENDING -> {
                        paymentOrder.markAsPending().incrementRetry().updatedAt(LocalDateTime.now())
                        paymentRetryStatusAdapter.scheduleRetry(paymentOrder)
                    }

                    else -> {
                        logger.warn("${paymentOrder.paymentOrderId} failed with non-final PSP status: ${paymentOrderStatusCheck.status}")
                        val failed = paymentOrder.markAsFinalizedFailed().incrementRetry()
                        paymentOrderRepository.save(failed)
                    }
                }

            } catch (e: Exception) {
                logger.error("Transient error during PSP status check for ${paymentOrder}, rescheduling", e)
                val updatedOrder = paymentOrder.markAsPending().incrementRetry().withRetryReason("transient error")
                    .withLastError(e.message)
                //do we want to ssave
                paymentOrderRepository.save(updatedOrder);
                // eeror in status check reschedukle  via redis
                paymentRetryStatusAdapter.scheduleRetry(paymentOrder)
            }
        }
    }


    private fun handleSuccess(envelope: EventEnvelope<DuePaymentOrderStatusCheck>, order: PaymentOrder) {
        val updatedOrder = order.markAsPaid()
        paymentOrderRepository.save(updatedOrder)
        val publishedEvent = paymentEventPublisher.publish(
            event = EventMetadatas.PaymentOrderSuccededMetaData,
            aggregateId = order.paymentOrderId,
            data = PaymentOrderSucceeded(
                paymentOrderId = updatedOrder.paymentOrderId,
                sellerId = updatedOrder.sellerId,
                amountValue = updatedOrder.amount.value,
                currency = updatedOrder.amount.currency
            ),
            parentEnvelope = envelope,
        )


        logger.info("📨 Emitted follow-up event with ID=${publishedEvent.eventId}")        //todo do not push yet.then weiwill add eventmetatada
    }

    private fun handleSchedulePaymentStatusCheck(order: PaymentOrder, reason: String? = "", error: String? = "") {
        val failedOrder = order.markAsFailed().incrementRetry().withRetryReason(reason).withLastError(error).updatedAt(
            LocalDateTime.now()
        )
        paymentOrderRepository.save(failedOrder)
        paymentRetryStatusAdapter.scheduleRetry(failedOrder)
            logger.warn("🔁 Retrying order=${failedOrder.paymentOrderId} (retry=${failedOrder.retryCount}): reason=$reason")

    }



    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        return CompletableFuture.supplyAsync {
            pspClient.checkPaymentStatus(order.paymentOrderId)
        }.get(3, TimeUnit.SECONDS)
        // This should be replaced with your actual PSP integration
    }
}