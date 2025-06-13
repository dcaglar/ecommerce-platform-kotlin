package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PSPClientPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

//todo  appply sama logic as in retry command executor

@Component
class ScheduledPaymentStatusCheckExecutor(
    val pspClient: PSPClientPort,
    val paymentService: PaymentService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    val executor = Executors.newSingleThreadExecutor()

    // For higher concurrency, inject a shared executor as a constructor parameter or as a class val.
    // For demo purposes, creating a new executor each time is OK.
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderStatusCheckRequested>>) {
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
            logger.info("▶️ [Handle Start] Processing PaymentOrderStatusCheckRequested")
            try {
                val response = safePspCall(order)
                logger.info("✅ PSP status returned status=$response for paymentOrderId=${order.paymentOrderId}")
                paymentService.processPspResult(
                    event = paymentOrderStatusCheckRequested,
                    pspStatus = response,
                )
            } catch (e: TimeoutException) {
                logger.error("⏱️ PSP status timed out for orderId=${order.paymentOrderId}, retrying...", e)
                paymentService.processPspResult(
                    event = paymentOrderStatusCheckRequested,
                    pspStatus = PaymentOrderStatus.TIMEOUT
                )
            } catch (e: Exception) {
                logger.error(
                    "❌ Unexpected error checking status orderId=${order.paymentOrderId}, retrying...: ${e.message}",
                    e
                )
                paymentService.processPspResult(
                    event = paymentOrderStatusCheckRequested,
                    pspStatus = PaymentOrderStatus.UNKNOWN
                )
            }
        }
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        return try {
            executor.submit<PaymentOrderStatus> {
                // If you want to time the call, do it here.
                pspClient.checkPaymentStatus(order.paymentOrderId.toString())
            }.get(3, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
        }
    }

}