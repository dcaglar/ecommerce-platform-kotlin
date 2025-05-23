package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.*
import com.dogancaglar.paymentservice.domain.event.mapper.toRetryEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.EventPublisherPort
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.dogancaglar.paymentservice.psp.PSPClient
import com.dogancaglar.paymentservice.psp.PSPStatusMapper
import io.micrometer.core.instrument.MeterRegistry
import jakarta.transaction.Transactional
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderExecutor(
    private val paymentOrderRepository: PaymentOrderRepository,
    @Qualifier("paymentRetryStatusAdapter")
    val paymentRetryStatusAdapter: RetryQueuePort<ScheduledPaymentOrderStatusRequest>,
    @Qualifier("paymentRetryPaymentAdapter") val paymentRetryPaymentAdapter: RetryQueuePort<PaymentOrderRetryRequested>,
    val pspClient: PSPClient,
    val paymentEventPublisher: EventPublisherPort,val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        val envelope = record.value()
        LogContext.with(envelope, mapOf(
            LogFields.TOPIC_NAME to record.topic(),
            LogFields.CONSUMER_GROUP to "payment-order-executor",
            LogFields.PAYMENT_ORDER_ID to envelope.data.paymentOrderId
        )) {
            logger.info("▶️ [Handle Start] Processing PaymentOrderCreated")
            val paymentOrderCreatedEvent = envelope.data
            val order = paymentOrderCreatedEvent.toDomain()
            if (order.status != PaymentOrderStatus.INITIATED) {
                logger.info("⏩ Skipping already processed order with status=${order.status}")
                return@with
            }
            try {
                    val response = safePspCall(order)
                    logger.info("✅ PSP call returned status=$response for paymentOrderId=${order.paymentOrderId}")
                    when {
                        response == PaymentOrderStatus.SUCCESSFUL -> {
                            handleSuccess(envelope,order.markAsPaid())
                        }
                        //TODO PAYMENT_NOT__SCUCESFUL_EVENT PUBLISH
                        PSPStatusMapper.requiresRetryPayment(response) -> {
                            handleRetryPayment(envelope,order, reason = "Retryable status from PSP: $response")

                        }

                        PSPStatusMapper.requiresStatusCheck(response) -> {
                            handleSchedulePaymentStatusCheck(parentEventEnvelope = envelope,
                                order=order,
                                reason = "Scheduling a   status check from PSP: $response"
                            )
                        }

                        else -> {
                            handleNonRetryable(order, response)
                        }

                    }
                } catch (e: TimeoutException) {
                    logger.error("⏱️ PSP call timed out for orderId=${order.paymentOrderId}, retrying...", e)
                    handleRetryPayment(envelope,order, "TIMEOUT", e.message)
                } catch (e: Exception) {
                    logger.error("❌ Unexpected error processing orderId=${order.paymentOrderId}, retrying...: ${e.message}", e)
                    handleRetryPayment(envelope,order, e.message, e.message)
                }
            }

    }

    private fun handleSuccess(envelope: EventEnvelope<PaymentOrderCreated>, order: PaymentOrder) {
        meterRegistry.counter("payment.success.total").increment()
        val updatedOrder = order.markAsPaid()
            paymentOrderRepository.save(updatedOrder)
        val publishedEvent = paymentEventPublisher.publish(
                event = EventMetadatas.PaymentOrderSuccededMetaData,
                aggregateId = updatedOrder.paymentOrderId,
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

    private fun handleSchedulePaymentStatusCheck(parentEventEnvelope: EventEnvelope<PaymentOrderCreated>,order: PaymentOrder, reason: String? = "", error: String? = "") {
        //prepre schedul request for future but emit PAyment_Scheduled with parent payment_request
        val failedOrder = order.markAsFailed().incrementRetry().withRetryReason(reason).withLastError(error).updatedAt(
            LocalDateTime.now()
        )
        paymentOrderRepository.save(failedOrder)
        paymentRetryStatusAdapter.scheduleRetry(failedOrder)
        val paymentOrderStatusScheduled = failedOrder.toPaymentOrderStatusScheduled(reason, error)
        paymentEventPublisher.publish(
            event = EventMetadatas.PaymentOrderStatusCheckScheduledMetadata,
            aggregateId = failedOrder.paymentOrderId,
            data = paymentOrderStatusScheduled,
            parentEnvelope = parentEventEnvelope
        )
    }


    private fun handleRetryPayment(parentEnvelope : EventEnvelope<PaymentOrderCreated> ,order: PaymentOrder, reason: String? = "", error: String? = "") {
        val failedOrder = order.markAsFailed().incrementRetry().withRetryReason(reason).withLastError(error).updatedAt(
            LocalDateTime.now()
        )
        meterRegistry.counter("payment.retry.attempts.total").increment()
        meterRegistry.counter("payment.retry.attempts", "reason", reason ?: "unknown").increment()
        paymentOrderRepository.save(failedOrder)
        if (failedOrder.retryCount < 5) {

            paymentRetryPaymentAdapter.scheduleRetry(failedOrder)
            //publish payment_not_succesful_event or retried event
            val retryPaymentEvent = failedOrder.toRetryEvent()
            paymentEventPublisher.publish(event = EventMetadatas.PaymentOrderRetryRequestedMetadata,
                aggregateId = failedOrder.paymentOrderId,
                data = retryPaymentEvent,
                parentEnvelope =parentEnvelope
            )
            logger.warn("🔁 Retrry payment  order=${failedOrder.paymentOrderId} (retry=${failedOrder.retryCount}) reason=$reason, lastError=$error")
        }
        else {
            logger.error("Max retries exceeded for ${failedOrder.paymentOrderId}. Marking as failed permanently.")
        }
    }

    private fun handleNonRetryable(order: PaymentOrder, status: PaymentOrderStatus) {
        val finalizedOrder = order.markAsFinalizedFailed()
            .withRetryReason("Non-retryable PSP status: $status").updatedAt(
                LocalDateTime.now()
            )

        paymentOrderRepository.save(finalizedOrder)
        logger.warn("❗ Order=${order.paymentOrderId} marked as permanently failed with PSP status=$status")
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        return CompletableFuture.supplyAsync {
            pspClient.charge(order)
        }.get(3, TimeUnit.SECONDS)
        // This should be replaced with your actual PSP integration
    }

    /*
    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
    return meterRegistry.timer("psp.call.duration").recordCallable {
        CompletableFuture.supplyAsync {
            pspClient.charge(order)
        }.get(3, TimeUnit.SECONDS)
    }
}
     */
}