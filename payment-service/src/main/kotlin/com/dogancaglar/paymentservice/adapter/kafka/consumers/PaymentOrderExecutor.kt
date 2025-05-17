package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
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
    @Qualifier("paymentRetryAdapter") val paymentRetryQueueAdapter: RetryQueuePort,
    val pspClient: PSPClient,
    val paymentEventPublisher: PaymentEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        val envelope = record.value()
        LogContext.with(envelope){
            MDC.put(LogFields.TOPIC_NAME,record.topic())
            MDC.put(LogFields.CONSUMER_GROUP,record.topic())
            MDC.put(LogFields.PAYMENT_ORDER_ID,envelope.data.paymentOrderId)

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
                            handleSuccess(order.markAsPaid())
                        }

                        PSPStatusMapper.requiresRetryPayment(response) -> {
                            handleRetryPayment(order, reason = "Retryable status from PSP: $response")
                        }

                        PSPStatusMapper.requiresStatusCheck(response) -> {
                            handleSchedulePaymentStatusCheck(
                                order,
                                reason = "Scheduling a   status check from PSP: $response"
                            )
                        }

                        else -> {
                            handleNonRetryable(order, response)
                        }

                    }
                } catch (e: TimeoutException) {
                    logger.error("⏱️ PSP call timed out for orderId=${order.paymentOrderId}, retrying...", e)
                    handleRetryPayment(order, "TIMEOUT", e.message)
                } catch (e: Exception) {
                    logger.error("❌ Unexpected error processing orderId=${order.paymentOrderId}, retrying...: ${e.message}", e)
                    handleRetryPayment(order, e.message, e.message)
                }
            }

    }

    private fun handleSuccess(order: PaymentOrder) {
        val updatedOrder = order.markAsPaid()
        paymentOrderRepository.save(updatedOrder)
        logger.info("🎉 Payment succeeded and saved: paymentOrderId=${order.paymentOrderId}")
        //todo do not push yet.then weiwill add eventmetatada
    }

    private fun handleSchedulePaymentStatusCheck(order: PaymentOrder, reason: String? = "", error: String? = "") {
        val failedOrder = order.markAsFailed().incrementRetry().withRetryReason(reason).withLastError(error).updatedAt(
            LocalDateTime.now()
        )
        paymentOrderRepository.save(failedOrder)
        if (failedOrder.retryCount < 5) {
            paymentRetryQueueAdapter.scheduleRetry(order.paymentOrderId, order.retryCount)
            logger.warn("🔁 Retrying order=${failedOrder.paymentOrderId} (retry=${failedOrder.retryCount}): reason=$reason")
        } else {
            logger.error("🚫 Max retries exceeded during retry payment for order=${failedOrder.paymentOrderId}")
        }
    }


    private fun handleRetryPayment(order: PaymentOrder, reason: String? = "", error: String? = "") {
        val failedOrder = order.markAsFailed().incrementRetry().withRetryReason(reason).withLastError(error).updatedAt(
            LocalDateTime.now()
        )
        paymentOrderRepository.save(failedOrder)
        if (failedOrder.retryCount < 5) {
            paymentRetryQueueAdapter.scheduleRetry(order.paymentOrderId, order.retryCount)
            logger.warn("Scheduled retry for ${failedOrder.paymentOrderId} (retry ${failedOrder.retryCount}): $reason")
        } else {
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
}