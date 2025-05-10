package com.dogancaglar.paymentservice.adapter.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreatedEvent
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryEvent
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceededEvent
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.psp.PSPClient
import com.dogancaglar.paymentservice.psp.PSPResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Service
class PaymentOrderExecutor(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val pspClient: PSPClient,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)



    @KafkaListener(topics = ["payment_order_created"], groupId = "payment-executor-group")
    @Transactional
    fun processInitialPayment(record: ConsumerRecord<String, String>) {
        val envelopeType = objectMapper
            .typeFactory
            .constructParametricType(EventEnvelope::class.java, PaymentOrderCreatedEvent::class.java)

        val envelope: EventEnvelope<PaymentOrderCreatedEvent> =
            objectMapper.readValue(record.value(), envelopeType)
        val event: PaymentOrderCreatedEvent = envelope.data
        val order = event.toDomain()
        try {

            if (order.status != PaymentOrderStatus.INITIATED) return

            val response = safePspCall(order)

            if (response.status == "SUCCESS") {
                val updatedOrder = order.markAsPaid()
                paymentOrderRepository.saveAll(listOf(updatedOrder))

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
            } else {
                val updatedOrder = order.markAsFailed();
                paymentOrderRepository.saveAll(listOf(updatedOrder))
                paymentEventPublisher.publish(
                    topic = "payment_order_retry",
                    aggregateId = order.paymentOrderId,
                    eventType = "payment_order_retry",
                    data = PaymentOrderRetryEvent(
                        paymentOrderId = order.paymentOrderId,
                        paymentId = order.paymentId,
                        sellerId = order.sellerId,
                        amountValue = order.amount.value,
                        currency = order.amount.currency,
                        retryCount = 0,
                        status = PaymentOrderStatus.FAILED.name,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now(),
                    )
                )
            }

        } catch (e: Exception) {
            logger.error("Failed to process payment_order_created:,retrying ${e.message}", e)
            val updatedOrder = order.markAsFailed();
            paymentOrderRepository.saveAll(listOf(updatedOrder))
            paymentEventPublisher.publish(
                topic = "payment_order_retry",
                aggregateId = order.paymentOrderId,
                eventType = "payment_order_retry",
                data = PaymentOrderRetryEvent(
                    paymentOrderId = order.paymentOrderId,
                    paymentId = order.paymentId,
                    sellerId = order.sellerId,
                    amountValue = order.amount.value,
                    currency = order.amount.currency,
                    retryCount = 0,
                    status = PaymentOrderStatus.FAILED.name,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                )
            )
        }
    }

    private fun safePspCall(order: PaymentOrder): PSPResponse {
        return CompletableFuture.supplyAsync {
            pspClient.charge(order)
        }.get(3, TimeUnit.SECONDS)
    }
}