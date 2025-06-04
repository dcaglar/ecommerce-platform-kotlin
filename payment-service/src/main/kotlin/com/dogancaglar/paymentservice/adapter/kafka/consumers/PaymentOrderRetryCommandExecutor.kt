package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.psp.PSPClient
import jakarta.transaction.Transactional
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderRetryCommandExecutor(
    private val paymentService: PaymentService,
    val pspClient: PSPClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>) {
        val envelope = record.value()
        val paymentOrderCreatedEvent = envelope.data
        val order = paymentService.mapEventToDomain(paymentOrderCreatedEvent)
        LogContext.with(
            envelope, mapOf(
                LogFields.TOPIC_NAME to record.topic(),
                LogFields.CONSUMER_GROUP to "PAYMENT_RETRY_EXECUTOR",
                LogFields.PUBLIC_PAYMENT_ID to envelope.data.publicPaymentId,
                LogFields.PUBLIC_PAYMENT_ORDER_ID to envelope.data.publicPaymentOrderId,
            )
        ) {
            logger.info("▶️ [Handle Start] Processing PaymentOrderRetryCommandExecutor")
            //todo any check is needed
            try {
                val response = safePspCall(order)
                logger.info("✅ PSP call PaymentOrderRetryCommandExecutor returned status=$response for paymentOrderId=${order.paymentOrderId}")
                paymentService.processPspResult(
                    event = paymentOrderCreatedEvent,
                    pspStatus = response
                )
            } catch (e: TimeoutException) {
                logger.error(
                    "⏱️ PSP call PaymentOrderRetryCommandExecutor timed out for orderId=${order.paymentOrderId}, retrying...",
                    e
                )
                paymentService.processPspResult(
                    event = paymentOrderCreatedEvent,
                    pspStatus = PaymentOrderStatus.TIMEOUT
                )
            } catch (e: Exception) {
                logger.error(
                    "❌ Unexpected error  processing PaymentOrderRetryCommandExecutor orderId=${order.paymentOrderId}, retrying...: ${e.message}",
                    e
                )
                paymentService.processPspResult(
                    event = paymentOrderCreatedEvent,
                    pspStatus = PaymentOrderStatus.UNKNOWN
                )

            }
        }

    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        return CompletableFuture.supplyAsync {
            pspClient.chargeRetry(order)
        }.get(3, TimeUnit.SECONDS)
        // This should be replaced with your actual PSP integration
    }


}