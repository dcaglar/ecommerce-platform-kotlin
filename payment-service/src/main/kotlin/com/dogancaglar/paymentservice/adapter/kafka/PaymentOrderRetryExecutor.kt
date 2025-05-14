package com.dogancaglar.paymentservice.adapter.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryEvent
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceededEvent
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.dogancaglar.paymentservice.psp.PSPClient
import com.dogancaglar.paymentservice.psp.PSPResponse
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
class PaymentOrderRetryExecutor(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val pspClient: PSPClient,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val objectMapper: ObjectMapper,
    @Qualifier("paymentRetryQueue")
    private val paymentRetryQueue: RetryQueuePort,
    @Qualifier("paymentStatusQueue")
    private val paymentStatusQueue: RetryQueuePort
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["payment_order_retry"], groupId = "payment-retry-executor-group")
    @Transactional
    fun retryProcessPayment(record: ConsumerRecord<String, String>) {
        try {
            val envelopeType = objectMapper
                .typeFactory
                .constructParametricType(EventEnvelope::class.java, PaymentOrderRetryEvent::class.java)
            val envelope: EventEnvelope<PaymentOrderRetryEvent> =
                objectMapper.readValue(record.value(), envelopeType)

            val event = envelope.data
            val paymentOrder = event.toDomain()
            val response = safePspStatusCall(paymentOrder)

            when (PaymentOrderStatus.valueOf(response.status)) {
                PaymentOrderStatus.SUCCESSFUL -> {
                    logger.info("${paymentOrder.paymentOrderId} processed successfully")
                    val successfulOrder = paymentOrder.markAsPaid().incrementRetry()
                    paymentOrderRepository.save(successfulOrder)

                    paymentEventPublisher.publish(
                        topic = "payment_order_success",
                        aggregateId = successfulOrder.paymentOrderId,
                        eventType = "payment_order_success",
                        data = PaymentOrderSucceededEvent(
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
                    paymentStatusQueue.scheduleRetry(
                        updatedOrder.paymentOrderId,
                        calculateBackoffMillis(updatedOrder.retryCount)
                    )
                }

                PaymentOrderStatus.DECLINED -> {
                    logger.warn("${paymentOrder.paymentOrderId} declined, scheduling retry")
                    val declinedOrder = paymentOrder.markAsFailed().incrementRetry()
                    paymentOrderRepository.save(declinedOrder)
                    paymentRetryQueue.scheduleRetry(
                        declinedOrder.paymentOrderId,
                        calculateBackoffMillis(declinedOrder.retryCount)
                    )
                }

                else -> {
                    logger.warn("${paymentOrder.paymentOrderId} unknown PSP status: ${response.status}, scheduling status check")
                    val unknownOrder = paymentOrder.markAsFailed().incrementRetry()
                    paymentOrderRepository.save(unknownOrder)
                    paymentStatusQueue.scheduleRetry(
                        unknownOrder.paymentOrderId,
                        calculateBackoffMillis(unknownOrder.retryCount)
                    )
                }
            }

        } catch (e: Exception) {
            val topic = record.topic()
            val partition = record.partition()
            logger.error("Exception occurred when consuming record from topic $topic, partition = $partition", e)
            try {
                val envelopeType = objectMapper
                    .typeFactory
                    .constructParametricType(EventEnvelope::class.java, PaymentOrderRetryEvent::class.java)
                val envelope: EventEnvelope<PaymentOrderRetryEvent> =
                     objectMapper.readValue(record.value(), envelopeType)

                paymentRetryQueue .scheduleRetry(
                    envelope.data.paymentOrderId,
                    calculateBackoffMillis(envelope.data.retryCount)
                )
            } catch (ex: Exception) {
                logger.error("Failed to schedule retry after deserialization failure", ex)
            }
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
}