import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.dogancaglar.paymentservice.psp.PSPClient
import com.dogancaglar.paymentservice.psp.PSPResponse
import com.dogancaglar.paymentservice.psp.PSPStatusMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Component
class PaymentOrderExecutor(
            private val paymentOrderRepository: PaymentOrderRepository,
          @Qualifier("paymentRetryQueueAdapter") val paymentRetryQueueAdapter:RetryQueuePort,
            val pspClient: PSPClient,
            val paymentEventPublisher: PaymentEventPublisher){
    private val logger = LoggerFactory.getLogger(javaClass)

    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        val event = record.value()
        logger.info("Received payment order created request: $event")

        val key = record.key()
        val envelope : EventEnvelope<PaymentOrderCreated> = record.value()
        val paymentOrderCreatedEvent = envelope.data
        val order = paymentOrderCreatedEvent.toDomain()
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
            paymentRetryQueueAdapter.scheduleRetry(
                paymentOrderId = updatedOrder.paymentOrderId,
                calculateBackoffMillis(updatedOrder.retryCount)
            )
        }
        // handle logic...
    }
    private fun handleSuccess(order: PaymentOrder) {
        val updatedOrder = order.markAsPaid()
        paymentOrderRepository.save(updatedOrder)
        paymentEventPublisher.publish(
            topic = "payment_order_success",
            aggregateId = updatedOrder.paymentOrderId,
            eventType = "payment_order_success",
            data = PaymentOrderSucceeded(
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

        if (failedOrder.retryCount < 5) {
            paymentRetryQueueAdapter.scheduleRetry(
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