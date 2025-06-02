package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.event.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.psp.PSPClient
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class ScheduledPaymentStatusCheckExecutor(
    val pspClient: PSPClient,
    val paymentService: PaymentService
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>) {
        val eventId = record.key()
        val envelope = record.value()
        val paymentOrderStatusCheckRequested = envelope.data
        val order = paymentService.mapEventToDomain(paymentOrderStatusCheckRequested)
        LogContext.with(
            envelope, mapOf(
                LogFields.TOPIC_NAME to record.topic(),
                LogFields.CONSUMER_GROUP to "payment-status-queue",
                LogFields.PUBLIC_PAYMENT_ID to envelope.data.publicPaymentId,
                LogFields.PUBLIC_PAYMENT_ORDER_ID to envelope.data.publicPaymentOrderId,
            )
        ) {
            logger.info("▶️ [Handle Start] Processing PaymentOrderStatusCheckRequested ")
            try {
                val response = safePspCall(order)
                logger.info("✅ PSP status returned status=$response for paymentOrderId=${order.paymentOrderId}")
                paymentService.processPspResult(event = paymentOrderStatusCheckRequested, pspStatus = response,envelope.eventId)
            } catch (e: TimeoutException) {
                logger.error("⏱️ PSP status timed out for orderId=${order.paymentOrderId}, retrying...", e)
                paymentService.processPspResult(event = paymentOrderStatusCheckRequested, pspStatus = PaymentOrderStatus.TIMEOUT,envelope.eventId)
            } catch (e: Exception) {
                logger.error(
                    "❌ Unexpected error checking status orderId=${order.paymentOrderId}, retrying...: ${e.message}",
                    e
                )
                paymentService.processPspResult(event = paymentOrderStatusCheckRequested, pspStatus = PaymentOrderStatus.UNKNOWN,envelope.eventId)

            }
        }

    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        return CompletableFuture.supplyAsync {
            pspClient.checkPaymentStatus(order.paymentOrderId.toString())
        }.get(3, TimeUnit.SECONDS)
        // This should be replaced with your actual PSP integration
    }

}