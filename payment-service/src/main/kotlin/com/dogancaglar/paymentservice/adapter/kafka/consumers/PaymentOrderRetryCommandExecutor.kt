package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.redis.PaymentRetryPaymentAdapter
import com.dogancaglar.paymentservice.adapter.redis.PaymentRetryStatusAdapter
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.event.ScheduledPaymentOrderStatusRequest
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
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
class PaymentOrderRetryCommandExecutor(private val paymentOrderRepository: PaymentOrderRepository,
                                       @Qualifier("paymentRetryStatusAdapter")
                                       val paymentRetryStatusAdapter: PaymentRetryStatusAdapter,
                                       @Qualifier("paymentRetryPaymentAdapter") val paymentRetryPaymentAdapter: PaymentRetryPaymentAdapter,
                                       val pspClient: PSPClient,
                                       val paymentEventPublisher: PaymentEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>) {
        val eventId = record.key()
        val envelope = record.value()
        LogContext.with(envelope) {
            MDC.put(LogFields.TOPIC_NAME, record.topic())
            MDC.put(LogFields.CONSUMER_GROUP, record.topic())
            MDC.put(LogFields.PAYMENT_ORDER_ID, envelope.data.paymentOrderId)
                val paymentOrderRetryRequestedEvent = envelope.data
                val paymentOrder = paymentOrderRetryRequestedEvent.toDomain()
                try {
                    val response = safePspRetryCall(paymentOrder)

                    when {
                        response == PaymentOrderStatus.SUCCESSFUL -> {
                            handleSuccess(order = paymentOrder)
                        }
                        //TODO PAYMENT_NOT__SCUCESFUL_EVENT PUBLISH
                        PSPStatusMapper.requiresRetryPayment(response) -> {
                            handleRetryPayment(paymentOrder, reason = "Retryable status from PSP: $response")
                        }

                        PSPStatusMapper.requiresStatusCheck(response) -> {
                            handleSchedulePaymentStatusCheck(
                                paymentOrder,
                                reason = "Scheduling a   status check from PSP: $response"
                            )
                        }

                        else -> {
                            handleNonRetryable(paymentOrder, response)
                        }
                    }
                } catch (e: TimeoutException) {
                    logger.error("Request get timeout, retrying payment  ${e.message}", e)
                    handleRetryPayment(paymentOrder, "TIMEOUT", e.message)
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
                } finally {
                    MDC.clear()
                }

            }

    }
    private fun handleSuccess(order: PaymentOrder) {
        val updatedOrder = order.markAsPaid()
        paymentOrderRepository.save(updatedOrder)
        //todo do ontohing
    }

    private fun handleSchedulePaymentStatusCheck(order: PaymentOrder, reason: String?="",error:String?="") {
        val failedOrder = order.incrementRetry().withRetryReason(reason).withLastError(error)
        if(failedOrder.retryCount>5) {
            logger.warn("Nr of retry exceeded 5")
            val finalizedFailed = failedOrder.markAsFinalizedFailed();
            paymentOrderRepository.save(finalizedFailed)
        }
        else {
            val retryStatusOrder =failedOrder.markAsPending()
            paymentOrderRepository.save(retryStatusOrder)
            paymentRetryStatusAdapter.scheduleRetry(retryStatusOrder)
            logger.warn("Scheduled retry status for ${retryStatusOrder.paymentOrderId} (retry ${retryStatusOrder.retryCount}): $reason")
        }
    }


    private fun handleRetryPayment(order: PaymentOrder, reason: String?="",error:String?="") {
        val failedOrder = order.markAsFailed().incrementRetry().withRetryReason(reason).withLastError(error).updatedAt(
            LocalDateTime.now())
        paymentOrderRepository.save(failedOrder)
        if (failedOrder.retryCount < 5) {
            paymentRetryPaymentAdapter.scheduleRetry(failedOrder)
            logger.warn("Scheduled retry for ${failedOrder.paymentOrderId} (retry ${failedOrder.retryCount}): $reason")
        } else {
            logger.error("Max retries exceeded for ${failedOrder.paymentOrderId}. Marking as failed permanently.")
        }
    }

    private fun handleNonRetryable(order: PaymentOrder, status: PaymentOrderStatus) {
        val finalizedOrder = order.markAsFinalizedFailed().incrementRetry().updatedAt(
            LocalDateTime.now())
            .withRetryReason("Non-retryable PSP status: $status")

        paymentOrderRepository.save(finalizedOrder)
        logger.warn("PaymentOrder ${order.paymentOrderId} failed with non-retryable status $status")
    }
    private fun safePspRetryCall(order: PaymentOrder): PaymentOrderStatus {
        return CompletableFuture.supplyAsync {
            pspClient.chargeRetry(order)
        }.get(3, TimeUnit.SECONDS)
    }

}