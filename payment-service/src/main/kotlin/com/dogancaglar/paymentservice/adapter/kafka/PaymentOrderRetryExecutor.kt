package com.dogancaglar.paymentservice.adapter.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryEvent
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceededEvent
import com.dogancaglar.paymentservice.domain.event.toDomain
import com.dogancaglar.paymentservice.domain.event.toIncremented
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
class PaymentOrderRetryExecutor(
    private val paymentOrderRepository: PaymentOrderRepository,
    private val pspClient: PSPClient,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)



    @KafkaListener(topics = ["payment_order_retry"], groupId = "payment-retry-executor-group")
    @Transactional
    fun processInitialPayment(record: ConsumerRecord<String, String>) {
        val envelopeType = objectMapper
            .typeFactory
            .constructParametricType(EventEnvelope::class.java, PaymentOrderRetryEvent::class.java)
        val envelope: EventEnvelope<PaymentOrderRetryEvent> =
            objectMapper.readValue(record.value(), envelopeType)

        try {

            val paymentOrderRetry = envelope.data.toDomain()
            if (paymentOrderRetry.status == PaymentOrderStatus.FAILED) {
                val response = safePspCall(paymentOrderRetry)
                if (response.status == "SUCCESS") {
                    logger.info("$paymentOrderRetry is processed succesfully")
                    val successfulOrder = paymentOrderRetry.markAsPaid()
                    paymentOrderRepository.saveAll(listOf(successfulOrder))
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
                } else if(response.status == "FAILED") {
                    val failedOrderRetryEvent = envelope.data.toIncremented()
                    if (failedOrderRetryEvent.retryCount > 5) {
                        val updateFinalizedOrder = failedOrderRetryEvent.toDomain().markAsFinalizedFailed()
                        logger.warn("$updateFinalizedOrder is failed  at attempt:${failedOrderRetryEvent.retryCount}, so finalize payment as failed")
                        paymentOrderRepository.saveAll(listOf(updateFinalizedOrder))
                    } else {
                        val updatedFailedOrder = failedOrderRetryEvent.toDomain().markAsFailed()
                        logger.warn("$failedOrderRetryEvent is failed  at attempt:${failedOrderRetryEvent.retryCount}, will retry later")
                        paymentOrderRepository.saveAll(listOf(updatedFailedOrder))
                        paymentEventPublisher.publish(
                            topic = "payment_order_retry",
                            aggregateId = updatedFailedOrder.paymentOrderId,
                            eventType = "payment_order_retry",
                            data = failedOrderRetryEvent
                        )

                    }
                }}
                else {
                    logger.info ("Skipping PaymentOrderRetryEvent ${paymentOrderRetry}")
                }
            }catch (e1 : Exception) {
            val failedOrderRetryEvent = envelope.data.toIncremented()
            val unknownOrderByTechIssue = failedOrderRetryEvent.toDomain().markAsFailed()
            logger.warn("$failedOrderRetryEvent is failed  at attempt:${failedOrderRetryEvent.retryCount}, will retry later")
            paymentOrderRepository.saveAll(listOf(unknownOrderByTechIssue))
            val data = failedOrderRetryEvent
            paymentEventPublisher.publish(
                topic = "payment_order_retry",
                aggregateId = unknownOrderByTechIssue.paymentOrderId,
                eventType = "payment_order_retry",
                data=data
            )

        }

        }



    private fun safePspCall(order: PaymentOrder): PSPResponse {
        return CompletableFuture.supplyAsync {
            pspClient.chargeRetry(order)
        }.get(3, TimeUnit.SECONDS)
    }
}