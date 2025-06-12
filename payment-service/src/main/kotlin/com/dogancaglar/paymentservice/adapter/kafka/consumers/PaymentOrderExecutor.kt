package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PSPClientPort
import io.micrometer.core.instrument.MeterRegistry
import jakarta.transaction.Transactional
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderExecutor(
    private val paymentService: PaymentService,
    private val pspClient: PSPClientPort,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        val envelope = record.value()
        val paymentOrderCreatedEvent = envelope.data
        val order = paymentService.mapEventToDomain(paymentOrderCreatedEvent)

        LogContext.with(
            envelope, mapOf(
                LogFields.TOPIC_NAME to record.topic(),
                LogFields.CONSUMER_GROUP to "PAYMENT_ORDER_EXECUTOR",
                LogFields.EVENT_ID to envelope.eventId.toString(),
                LogFields.TRACE_ID to envelope.traceId,
                LogFields.PUBLIC_PAYMENT_ID to envelope.data.publicPaymentId,
                LogFields.PUBLIC_PAYMENT_ORDER_ID to envelope.aggregateId
            )
        ) {
            // 1. Skip already-processed orders
            if (order.status != PaymentOrderStatus.INITIATED) {
                logger.info("⏩ Skipping already processed order with status=${order.status}")
                return@with
            }
            // 2. Try-catch-all: nothing leaves this block
            try {
                val response = safePspCall(order)
                logger.info("✅ PSP call returned status=$response for paymentOrderId=${order.paymentOrderId}")
                paymentService.processPspResult(event = paymentOrderCreatedEvent, pspStatus = response)
            } catch (e: TimeoutException) {
                logger.error("⏱️ PSP call timed out for orderId=${order.paymentOrderId}, retrying...", e)
                paymentService.processPspResult(
                    event = paymentOrderCreatedEvent,
                    pspStatus = PaymentOrderStatus.TIMEOUT
                )
            } catch (e: Exception) {
                logger.error("❌ Unexpected error for orderId=${order.paymentOrderId}, marking as UNKNOWN", e)
                paymentService.processPspResult(
                    event = paymentOrderCreatedEvent,
                    pspStatus = PaymentOrderStatus.UNKNOWN
                )
            } catch (t: Throwable) { // <--- Catches even OutOfMemory etc., logs, prevents crash
                logger.error("💥 Fatal throwable for orderId=${order.paymentOrderId}, lost event!", t)
                // Optionally: send to a "dead letter queue" or alert admin
            }
        }
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit<PaymentOrderStatus> {
                val start = System.nanoTime()
                try {
                    return@submit pspClient.charge(order)
                } finally {
                    val end = System.nanoTime()
                    meterRegistry.timer("psp.call.duration", "operation", "charge")
                        .record(end - start, TimeUnit.NANOSECONDS)
                }
            }.get(3, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
        }
    }
}