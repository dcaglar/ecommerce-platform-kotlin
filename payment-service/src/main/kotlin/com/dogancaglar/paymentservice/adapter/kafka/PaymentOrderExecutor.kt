package com.dogancaglar.paymentservice.adapter.kafka
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.dogancaglar.paymentservice.psp.PSPClient
import com.dogancaglar.paymentservice.psp.PSPStatusMapper
import jakarta.transaction.Transactional
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderExecutor(
    private val paymentOrderRepository: PaymentOrderRepository,
    @Qualifier("paymentRetryAdapter") val paymentRetryQueueAdapter: RetryQueuePort,
    val pspClient: PSPClient,
    val paymentEventPublisher: PaymentEventPublisher){
    private val logger = LoggerFactory.getLogger(javaClass)

        @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        val event = record.value()
        logger.info("Received payment order created request: $event")

        val key = record.key()
        val envelope : EventEnvelope<PaymentOrderCreated> = record.value()
        val paymentOrderCreatedEvent = envelope.data
        val order = paymentOrderCreatedEvent.toDomain()
        if (order.status != PaymentOrderStatus.INITIATED) return
        try {
            try {
                val response = safePspCall(order)
                when {
                    response == PaymentOrderStatus.SUCCESSFUL -> {
                        handleSuccess(order.markAsPaid())
                    }

                    PSPStatusMapper.requiresRetryPayment(response) -> {
                        handleRetryPayment(order, reason = "Retryable status from PSP: $response")
                    }
                    PSPStatusMapper.requiresStatusCheck(response) -> {
                            handleSchedulePaymentStatusCheck(order, reason = "Scheduling a   status check from PSP: $response")
                    } else -> {
                    handleNonRetryable(order,response)
                }

                }
            } catch (e: TimeoutException){
                logger.error("Request get timeout, retrying payment  ${e.message}", e)
                handleRetryPayment(order,"TIMEOUT",e.message)
            }

            catch (e: Exception) {
                logger.error("Failed to process payment_order_created:,retrying ${e.message}", e)
                handleRetryPayment(order,e.message,e.message)
            }
        } catch (e: Exception) {
            TODO("Not yet implemented")
        }
    }
    private fun handleSuccess(order: PaymentOrder) {
        val updatedOrder = order.markAsPaid()
        paymentOrderRepository.save(updatedOrder)
        //todo do not push yet.then weiwill add eventmetatada
    }

    private fun handleSchedulePaymentStatusCheck(order: PaymentOrder, reason: String?="",error:String?="") {
        val failedOrder = order.markAsFailed().incrementRetry().withRetryReason(reason).withLastError(error).updatedAt(
            LocalDateTime.now())
        paymentOrderRepository.save(failedOrder)
        if (failedOrder.retryCount < 5) {
            paymentRetryQueueAdapter.scheduleRetry(order.paymentOrderId,order.retryCount)
            logger.warn("Scheduled retry for ${failedOrder.paymentOrderId} (retry ${failedOrder.retryCount}): $reason")
        } else {
            logger.error("Max retries exceeded for ${failedOrder.paymentOrderId}. Marking as failed permanently.")
        }
    }


    private fun handleRetryPayment(order: PaymentOrder, reason: String?="",error:String?="") {
        val failedOrder = order.markAsFailed().incrementRetry().withRetryReason(reason).withLastError(error).updatedAt(
            LocalDateTime.now())
        paymentOrderRepository.save(failedOrder)
        if (failedOrder.retryCount < 5) {
            paymentRetryQueueAdapter.scheduleRetry(order.paymentOrderId,order.retryCount)
            logger.warn("Scheduled retry for ${failedOrder.paymentOrderId} (retry ${failedOrder.retryCount}): $reason")
        } else {
            logger.error("Max retries exceeded for ${failedOrder.paymentOrderId}. Marking as failed permanently.")
        }
    }

    private fun handleNonRetryable(order: PaymentOrder, status: PaymentOrderStatus) {
        val finalizedOrder = order.markAsFinalizedFailed()
            .withRetryReason("Non-retryable PSP status: $status").updatedAt(
                LocalDateTime.now())

        paymentOrderRepository.save(finalizedOrder)
        logger.warn("PaymentOrder ${order.paymentOrderId} failed with non-retryable status $status")
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        return CompletableFuture.supplyAsync {
            pspClient.charge(order)
        }.get(3, TimeUnit.SECONDS)
        // This should be replaced with your actual PSP integration
    }
}