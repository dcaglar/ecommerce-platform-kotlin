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
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.TransientDataAccessException
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


@Component
class PaymentOrderRetryCommandExecutor(
    private val paymentService: PaymentService,
    val pspClient: PSPClientPort,
    val retryMetrics: RetryMetrics,
    val pspResultCache: PspResultCachePort,
    @Qualifier("paymentOrderRetryExecutorPoolConfig") private val pspRetryExecutor: ThreadPoolTaskExecutor
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>) {
        val envelope = record.value()
        val event = envelope.data
        val order = paymentService.mapEventToDomain(event)
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
            val cacheStart = System.currentTimeMillis()
            try {
                val totalStart = System.currentTimeMillis()
                val key = order.paymentOrderId.toString()
                val cachedResult = pspResultCache.get(key)
                val cacheEnd = System.currentTimeMillis()
                logger.info("TIMING: PSP cache lookup took ${cacheEnd - cacheStart} ms for $key")

                val pspStart = System.currentTimeMillis()
                val status = if (cachedResult != null) {
                    logger.info("♻️ Cache hit for $key → $cachedResult")
                    PaymentOrderStatus.valueOf(cachedResult)
                } else {
                    val result = safePspCall(order)
                    pspResultCache.put(key, result.name)
                    logger.info("✅ PSP returned $result for $key")
                    result
                }
                val pspEnd = System.currentTimeMillis()
                logger.info("TIMING: PSP call (including cache, if miss) took ${pspEnd - pspStart} ms for $key")

                val dbStart = System.currentTimeMillis()
                paymentService.processPspResult(event = event, pspStatus = status)
                pspResultCache.remove(key)
                val dbEnd = System.currentTimeMillis()
                logger.info("TIMING: processPspResult (DB/write) took ${dbEnd - dbStart} ms for $key")

                val totalEnd = System.currentTimeMillis()
                logger.info("TIMING: Total handler time: ${totalEnd - totalStart} ms for $key")

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
        val future = pspRetryExecutor.submit<PaymentOrderStatus> { pspClient.chargeRetry(order) }
        return try {
            future.get(3, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("PSP retry call timed out for paymentOrderId=${order.paymentOrderId}, scheduling retry")
            return PaymentOrderStatus.TIMEOUT
        } finally {
            retryMetrics.recordRetryAttempt(order.retryCount, order.retryReason)
        }
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