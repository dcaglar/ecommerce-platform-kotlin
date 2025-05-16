package com.dogancaglar.paymentservice.adapter.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.dogancaglar.paymentservice.psp.PSPClient
import com.dogancaglar.paymentservice.psp.PSPResponse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Component
class PaymentRetryExecutor(private val paymentOrderRepository: PaymentOrderRepository,
                           @Qualifier("paymentRetryStatusAdapter") val paymentRetryStatusAdapter:RetryQueuePort,
                           val pspClient: PSPClient,
                           val paymentEventPublisher: PaymentEventPublisher): RetryQueuePort {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>) {
        val eventId = record.key()
        val envelope = record.value()
        val event = envelope.data
        val paymentOrder = event.toDomain()
        val response = safePspStatusCall(paymentOrder)
        try {
        when (PaymentOrderStatus.valueOf(response.status)) {
            PaymentOrderStatus.SUCCESSFUL -> {
                logger.info("${paymentOrder.paymentOrderId} processed successfully")
                val successfulOrder = paymentOrder.markAsPaid().withRetryReason(null)
                paymentOrderRepository.save(successfulOrder)

                paymentEventPublisher.publish(
                    topic = "payment_order_success",
                    aggregateId = successfulOrder.paymentOrderId,
                    eventType = "payment_order_success",
                    data = PaymentOrderSucceeded(
                        paymentOrderId = successfulOrder.paymentOrderId,
                        sellerId = successfulOrder.sellerId,
                        amountValue = successfulOrder.amount.value,
                        currency = successfulOrder.amount.currency
                    )
                )
            }

            PaymentOrderStatus.PENDING,
            PaymentOrderStatus.CAPTURE_PENDING -> {
                logger.info("${paymentOrder.paymentOrderId} returned status ${response.status}, scheduling status check")
                val updatedOrder = paymentOrder.markAsFailed().incrementRetry()
                paymentOrderRepository.save(updatedOrder)
                paymentRetryStatusAdapter.scheduleRetry(
                    updatedOrder.paymentOrderId,
                    calculateBackoffMillis(updatedOrder.retryCount)
                )
            }

            PaymentOrderStatus.DECLINED -> {
                logger.warn("${paymentOrder.paymentOrderId} declined, scheduling retry")
                val declinedOrder = paymentOrder.markAsFailed().incrementRetry()
                paymentOrderRepository.save(declinedOrder)
                paymentRetryStatusAdapter.scheduleRetry(
                    declinedOrder.paymentOrderId,
                    calculateBackoffMillis(declinedOrder.retryCount)
                )
            }

            else -> {
                logger.warn("${paymentOrder.paymentOrderId} unknown PSP status: ${response.status}, scheduling status check")
                val unknownOrder = paymentOrder.markAsFailed().incrementRetry()
                paymentOrderRepository.save(unknownOrder)
                paymentRetryStatusAdapter.scheduleRetry(
                    unknownOrder.paymentOrderId,
                    calculateBackoffMillis(unknownOrder.retryCount)
                )
            }
        }
    }
        catch (e: Exception) {
        val topic = record.topic()
        val partition = record.partition()
        val key = record.key()
        logger.error("Failed to process payment retry for eventId=$key from topic=$topic partition=$partition", e)

        // Optional: add to DLQ, monitoring queue, or emit an alert metric
    }

}
    private fun safePspStatusCall(order: PaymentOrder): PSPResponse {
        return CompletableFuture.supplyAsync {
            pspClient.chargeRetry(order)
        }.get(3, TimeUnit.SECONDS)
    }

    fun calculateBackoffMillis(retryCount: Int): Long {
        val baseDelay = 5_000L // 5 seconds
        return baseDelay * (retryCount + 1)
    }

    override fun scheduleRetry(paymentOrderId: String, delayMillis: Long) {
        TODO("Not yet implemented")
    }

    override fun pollDueRetries(): List<String> {
        TODO("Not yet implemented")
    }
}