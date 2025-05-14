package com.dogancaglar.paymentservice.adapter.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.redis.PaymentRetryQueueAdapter
import com.dogancaglar.paymentservice.adapter.redis.PaymentRetryStatusAdapter
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreatedEvent
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceededEvent
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.dogancaglar.paymentservice.psp.PSPClient
import com.dogancaglar.paymentservice.psp.PSPResponse
import com.dogancaglar.paymentservice.psp.PSPStatusMapper
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Service
class PaymentOrderExecutor(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val pspClient: PSPClient,
    private val paymentEventPublisher: PaymentEventPublisher,
    @Qualifier("paymentRetryQueue")
    private val paymentRetryQueue: RetryQueuePort,
    @Qualifier("paymentStatusQueue")
    private val paymentStatusQueue: RetryQueuePort,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val MAX_RETRIES = 5

    @KafkaListener(topics = ["payment_order_created"], groupId = "payment-executor-group")
    @Transactional
    fun onPaymentOrderCreated(record: ConsumerRecord<String, String>) {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, PaymentOrderCreatedEvent::class.java)

        val paymentOrderCreatedEvent: EventEnvelope<PaymentOrderCreatedEvent> =
            objectMapper.readValue(record.value(), envelopeType)
        val event: PaymentOrderCreatedEvent = paymentOrderCreatedEvent.data
        val order = event.toDomain()

        if (order.status != PaymentOrderStatus.INITIATED) return
        try {
            val response = safePspCall(order)
            val mappedStatus = PSPStatusMapper.fromPspStatus(response.status)
            when {
                mappedStatus == PaymentOrderStatus.SUCCESSFUL -> {
                    handleSuccess(order.markAsPaid())
                }

                PSPStatusMapper.isRetryable(mappedStatus) -> {
                    handleFailure(order, "Retryable status from PSP: $mappedStatus")
                } else -> {
                    handleNonRetryable(order,mappedStatus)
                }

            }
        } catch (e: Exception) {
            logger.error("Failed to process payment_order_created:,retrying ${e.message}", e)
            val updatedOrder = order.markAsFailed().incrementRetry();
            paymentOrderRepository.save(updatedOrder)
            paymentRetryQueue.scheduleRetry(
                paymentOrderId = updatedOrder.paymentOrderId,
                calculateBackoffMillis(updatedOrder.retryCount)
            )
        }

    }

    private fun handleSuccess(order: PaymentOrder) {
        val updatedOrder = order.markAsPaid()
        paymentOrderRepository.save(updatedOrder)
        paymentEventPublisher.publish(
            topic = "payment_order_success",
            aggregateId = updatedOrder.paymentOrderId,
            eventType = "payment_order_success",
            data = PaymentOrderSucceededEvent(
                paymentOrderId = updatedOrder.paymentOrderId,
                sellerId = updatedOrder.sellerId,
                amountValue = updatedOrder.amount.value,
                currency = updatedOrder.amount.currency
            )
        )
    }

    private fun handleFailure(order: PaymentOrder, reason: String) {
        val failedOrder = order.markAsFailed().incrementRetry().withRetryReason(reason)
        paymentOrderRepository.save(failedOrder)

        if (failedOrder.retryCount < MAX_RETRIES) {
            paymentStatusQueue.scheduleRetry(
                paymentOrderId = failedOrder.paymentOrderId,
                delayMillis = calculateBackoffMillis(retryCount = failedOrder.retryCount)
            )
            logger.warn("Scheduled retry for ${failedOrder.paymentOrderId} (retry ${failedOrder.retryCount}): $reason")
        } else {
            logger.error("Max retries exceeded for ${failedOrder.paymentOrderId}. Marking as failed permanently.")
        }
    }

    private fun handleNonRetryable(order: PaymentOrder, status: PaymentOrderStatus) {
        val finalizedOrder = order.markAsFinalizedFailed()
            .withRetryReason("Non-retryable PSP status: $status")

        paymentOrderRepository.save(finalizedOrder)
        logger.warn("PaymentOrder ${order.paymentOrderId} failed with non-retryable status $status")
    }

    fun calculateBackoffMillis(retryCount: Int): Long {
        val baseDelay = 5_000L // 5 seconds
        return baseDelay * (retryCount + 1) // Linear or exponential backoff
    }

    private fun safePspCall(order: PaymentOrder): PSPResponse {
        return CompletableFuture.supplyAsync {
            pspClient.charge(order)
        }.get(3, TimeUnit.SECONDS)
        // This should be replaced with your actual PSP integration
    }

}