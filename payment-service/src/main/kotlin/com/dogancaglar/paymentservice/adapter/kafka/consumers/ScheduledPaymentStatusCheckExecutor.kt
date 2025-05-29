package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.delayqueue.ScheduledPaymentOrderStatusService
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.redis.PaymentRetryPaymentAdapter
import com.dogancaglar.paymentservice.adapter.redis.PaymentRetryStatusAdapter
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.mapper.PaymentOrderEventMapper
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import com.dogancaglar.paymentservice.psp.PSPClient
import com.dogancaglar.paymentservice.psp.PSPStatusMapper
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
class PaymentOrderRetryCommandExecutor(
    private val paymentOrderRepository: PaymentOrderOutboundPort,
    @Qualifier("paymentRetryStatusAdapter")
    val paymentRetryStatusAdapter: PaymentRetryStatusAdapter,
    @Qualifier("paymentRetryPaymentAdapter") val paymentRetryPaymentAdapter: PaymentRetryPaymentAdapter,
    val scheduledPaymentOrderStatusService: ScheduledPaymentOrderStatusService,
    val pspClient: PSPClient,
    val paymentEventPublisher: PaymentEventPublisher,
    val paymentService: PaymentService
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>) {
        val eventId = record.key()
        val envelope = record.value()
        val paymentOrderRetryRequestedEvent = envelope.data
        val failedPaymentOrder = paymentService.fromRetryRequestedEvent(paymentOrderRetryRequestedEvent)
        LogContext.with(
            envelope, mapOf(
                LogFields.TOPIC_NAME to record.topic(),
                LogFields.CONSUMER_GROUP to "payment-order-executor",
                LogFields.PUBLIC_PAYMENT_ORDER_ID to envelope.data.paymentOrderId
            )
        ) {
            try {
                val response = safePspRetryCall(failedPaymentOrder)

                when {
                    response == PaymentOrderStatus.SUCCESSFUL -> {
                        handleSuccess(envelope = envelope, order = failedPaymentOrder)
                    }
                    //TODO PAYMENT_NOT__SCUCESFUL_EVENT PUBLISH
                    PSPStatusMapper.requiresRetryPayment(response) -> {
                        handleRetryPayment(failedPaymentOrder, reason = "Retryable status from PSP: $response")
                    }

                    PSPStatusMapper.requiresStatusCheck(response) -> {
                        handlePendingPayment(
                            envelope,
                            failedPaymentOrder,
                            reason = "Scheduling a   status check from PSP: $response"
                        )
                    }

                    else -> {
                        handleNonRetryable(failedPaymentOrder, response)
                    }
                }
            } catch (e: TimeoutException) {
                logger.error("Request get timeout, retrying payment")
                handleRetryPayment(failedPaymentOrder, "TIMEOUT", e.message)
            } catch (e: Exception) {
                val topic = record.topic()
                val partition = record.partition()
                val key = record.key()
                val paymentOrderRequested = record.value().data
                logger.error(
                    "Failed to process retry payment paymentOrderId=${paymentOrderRequested.paymentOrderId}eventId=$key from topic=$topic partition=$partition",
                    e
                )

                // Optional: add to DLQ, monitoring queue, or emit an alert metric
            }


        }

    }

    fun handleSuccess(envelope: EventEnvelope<PaymentOrderRetryRequested>, order: PaymentOrder) {
        val updatedOrder = paymentService.processSuccessfulPayment(order)

        val publishedEvent = paymentEventPublisher.publish(
            event = EventMetadatas.PaymentOrderSuccededMetaData,
            aggregateId = updatedOrder.publicPaymentOrderId,
            data = PaymentOrderEventMapper.toPaymentOrderSuccededEvent(updatedOrder),
            parentEnvelope = envelope,
        )


        logger.info("📨 Emitted follow-up event with ID=${publishedEvent.eventId}")        //todo do not push yet.then weiwill add eventmetatada
    }

    fun handlePendingPayment(
        envelope: EventEnvelope<PaymentOrderRetryRequested>,
        order: PaymentOrder,
        reason: String? = "",
        error: String? = ""
    ) {
        val pendingPaymentOrder = paymentService.processPendingPayment(order, reason, error)
        paymentRetryStatusAdapter.scheduleRetry(pendingPaymentOrder)
        val paymentOrderStatusScheduled = PaymentOrderEventMapper.toPaymentOrderStatusScheduled(pendingPaymentOrder)

        paymentEventPublisher.publish(
            event = EventMetadatas.PaymentOrderStatusCheckScheduledMetadata,
            aggregateId = pendingPaymentOrder.publicPaymentOrderId,
            data = paymentOrderStatusScheduled,
            parentEnvelope = envelope
        )
        MDC.put("retryCount", pendingPaymentOrder.retryCount.toString())
        MDC.put("retryReason", reason ?: "N/A")
        logger.warn("Scheduled retry status for ${pendingPaymentOrder.paymentOrderId} (retry ${pendingPaymentOrder.retryCount}): $reason")

    }


    fun handleRetryPayment(order: PaymentOrder, reason: String? = "", error: String? = "") {
        val failedOrderDecision = paymentService.handleRetryAttempt(order);
        val shouldRetry = !failedOrderDecision.second
        val failedOrder = failedOrderDecision.first
        MDC.put("retryCount", failedOrder.retryCount.toString())
        MDC.put("retryReason", reason ?: "N/A")
        if (shouldRetry) {
            paymentRetryPaymentAdapter.scheduleRetry(failedOrder)
            logger.warn("Scheduled retry for ${failedOrder} (retry ${failedOrder.retryCount}): $reason")
        } else {
            logger.error("Max retries exceeded for ${failedOrder.paymentOrderId}. Marking as failed permanently.")
        }
    }

    fun handleNonRetryable(order: PaymentOrder, status: PaymentOrderStatus) {
        val finalizedOrder = order.markAsFinalizedFailed().incrementRetry().updatedAt(
            LocalDateTime.now()
        )
            .withRetryReason("Non-retryable PSP status: $status")

        paymentOrderRepository.save(finalizedOrder)
        logger.warn("PaymentOrder ${order.paymentOrderId} failed with non-retryable status $status")
    }

    fun safePspRetryCall(order: PaymentOrder): PaymentOrderStatus {
        return CompletableFuture.supplyAsync {
            pspClient.chargeRetry(order)
        }.get(3, TimeUnit.SECONDS)
    }

    companion object {
        private const val MAX_RETRIES = 5
    }

}