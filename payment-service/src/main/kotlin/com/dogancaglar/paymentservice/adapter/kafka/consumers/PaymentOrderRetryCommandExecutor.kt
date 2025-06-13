package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PSPClientPort
import com.dogancaglar.paymentservice.domain.port.PspResultCachePort
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.errors.RetriableException
import org.slf4j.LoggerFactory
import org.springframework.dao.TransientDataAccessException
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderRetryCommandExecutor(
    private val paymentService: PaymentService,
    val pspClient: PSPClientPort,
    val retryMetrics: RetryMetrics,
    val pspResultCache: PspResultCachePort
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    val pspExecutor = Executors.newFixedThreadPool(32)
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>) {
        val envelope = record.value()
        val paymentOrderCreatedEvent = envelope.data
        val order = paymentService.mapEventToDomain(paymentOrderCreatedEvent)
        LogContext.with(
            envelope, mapOf(
                LogFields.TOPIC_NAME to record.topic(),
                LogFields.CONSUMER_GROUP to "PAYMENT_RETRY_EXECUTOR",
                LogFields.PUBLIC_PAYMENT_ID to envelope.data.publicPaymentId,
                LogFields.PUBLIC_PAYMENT_ORDER_ID to envelope.aggregateId,
                LogFields.EVENT_ID to envelope.eventId.toString(),
                LogFields.TRACE_ID to envelope.traceId,
                LogFields.PARENT_EVENT_ID to envelope.parentEventId.toString()
            )
        ) {
            try {
                val pspCacheKey = order.paymentOrderId.toString() // or use a more specific key if needed
                val cachedResult = pspResultCache.get(pspCacheKey)
                var response: PaymentOrderStatus?
                if (cachedResult != null) {
                    logger.info("PSP retry result  found in cache for orderId=${order.paymentOrderId}, skipping PSP call and using cached result.")
                    // Deserialize cachedResult and use it
                    response = PaymentOrderStatus.valueOf(cachedResult)
                } else {
                    try {
                        response = safePspCall(order)
                        //cache result
                        pspResultCache.put(pspCacheKey, response.name) // or serialize if needed
                        logger.info("✅ PSP call retry returned status=$response for paymentOrderId=${order.paymentOrderId}")
                    } catch (e: TimeoutException) {
                        logger.error("⏱️ PSP call timed out for orderId=${order.paymentOrderId}, will retry...", e)
                        response = PaymentOrderStatus.TIMEOUT
                    }
                }
                paymentService.processPspResult(
                    event = paymentOrderCreatedEvent,
                    pspStatus = response!!
                )
                pspResultCache.remove(pspCacheKey) // Clear cache after successful processing


            } catch (e: TransientDataAccessException) {
                logger.error("🔄 Retry Transient DB error for orderId=${order.paymentOrderId}, will be retried/DLQ", e)
                throw e // retry/DLQ
            } catch (e: RetriableException) {
                logger.error(
                    "🔄 Retry Transient Kafka publish error for orderId=${order.paymentOrderId}, will be retried/DLQ",
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
        return pspExecutor.submit<PaymentOrderStatus> {
            try {
                pspClient.chargeRetry(order)
            } finally {
                retryMetrics.recordRetryAttempt(order.retryCount, order.retryReason)
            }
        }.get(3, TimeUnit.SECONDS)
    }
}

@Component
class RetryMetrics(meterRegistry: MeterRegistry) {
    // If you want to tag by reason, you can keep a map or register with a static set of reasons
    private val retrySummary = DistributionSummary.builder("paymentorder.retry.attempts")
        .baseUnit("attempts")
        .description("Number of retry attempts before PaymentOrder succeeded")
        .register(meterRegistry)

    fun recordRetryAttempt(retryCount: Int, reason: String?) {
        // You can add .tag("reason", reason ?: "unknown") if you really want, but beware of unbounded label cardinality!
        retrySummary.record(retryCount.toDouble())
    }
}