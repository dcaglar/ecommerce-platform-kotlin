package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PSPClientPort
import com.dogancaglar.paymentservice.domain.port.PspResultCachePort
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.errors.RetriableException
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderExecutor(
    private val paymentService: PaymentService,
    private val pspClient: PSPClientPort,
    private val meterRegistry: MeterRegistry,
    private val pspResultCache: PspResultCachePort
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val pspExecutor = Executors.newFixedThreadPool(64) // or higher
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
                val pspCacheKey = order.paymentOrderId.toString() // or use a more specific key if needed
                val cachedResult = pspResultCache.get(pspCacheKey)
                var response: PaymentOrderStatus? = null
                if (cachedResult != null) {
                    logger.info("PSP result found in cache for orderId=${order.paymentOrderId}, skipping PSP call and using cached result.")
                    // Deserialize cachedResult and use it
                    response = PaymentOrderStatus.valueOf(cachedResult)
                } else {
                    logger.info("✅ PSP call returned status=$response for paymentOrderId=${order.paymentOrderId}")
                    try {
                        response = safePspCall(order)
                        //cache result
                        pspResultCache.put(pspCacheKey, response.name) // or serialize if needed
                        logger.info("✅ PSP call returned status=$response for paymentOrderId=${order.paymentOrderId}")
                    } catch (e: TimeoutException) {
                        logger.error("⏱️ PSP call timed out for orderId=${order.paymentOrderId}, will retry...", e)
                        response = PaymentOrderStatus.TIMEOUT
                    }
                }

                paymentService.processPspResult(event = paymentOrderCreatedEvent, pspStatus = response!!)
                pspResultCache.remove(pspCacheKey) // Clear cache after successful processing

            } catch (e: org.springframework.dao.TransientDataAccessException) {
                logger.error("🔄 Transient DB error for orderId=${order.paymentOrderId}, will be retried/DLQ", e)
                throw e // retry/DLQ
            } catch (e: RetriableException) {
                logger.error(
                    "🔄 Transient Kafka publish error for orderId=${order.paymentOrderId}, will be retried/DLQ",
                    e
                )
                throw e // retry/DLQ
            } catch (t: Throwable) {
                logger.error(
                    "❌ Non-transient, unexpected, or fatal error for orderId=${order.paymentOrderId}, will be sent to DLQ immediately (no retry)",
                    t
                )
                throw ListenerExecutionFailedException("Non-transient, unexpected, or fatal error, send to DLQ", t)
            }
        }
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        val start = System.nanoTime()
        try {
            val future = pspExecutor.submit<PaymentOrderStatus> {
                pspClient.charge(order)
            }
            try {
                return future.get(3, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                future.cancel(true)
                throw e
            }
        } finally {
            val end = System.nanoTime()
            meterRegistry.timer("psp.call.duration", "operation", "charge")
                .record(end - start, java.util.concurrent.TimeUnit.NANOSECONDS)
        }
    }
}


