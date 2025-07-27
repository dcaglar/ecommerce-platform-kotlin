package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.*

@Component("paymentRetryQueueAdapter")
class PaymentRetryQueueAdapter(
    val paymentRetryRedisCache: PaymentRetryRedisCache,
    meterRegistry: MeterRegistry,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) : RetryQueuePort<PaymentOrderRetryRequested> {

    init {
        Gauge.builder("redis_retry_zset_size") {
            paymentRetryRedisCache.zsetSize()
        }
            .description("Number of entries pending in the Redis retry ZSet")
            .register(meterRegistry)
    }

    private val logger = LoggerFactory.getLogger(PaymentRetryQueueAdapter::class.java)

    override fun scheduleRetry(
        paymentOrder: PaymentOrder,
        backOffMillis: Long,
        retryReason: String?,
        lastErrorMessage: String?
    ) {
        val totalStart = System.currentTimeMillis()
        try {
            val retryCountStart = System.currentTimeMillis()
            val retryCount = paymentRetryRedisCache.incrementAndGetRetryCount(paymentOrder.paymentOrderId.value)
            val retryCountEnd = System.currentTimeMillis()

            val retryAt = System.currentTimeMillis() + backOffMillis

            val eventMapStart = System.currentTimeMillis()
            val paymentRetryRequestEvent = PaymentOrderDomainEventMapper.toPaymentOrderRetryRequestEvent(
                order = paymentOrder,
                newRetryCount = retryCount,
                retryReason = retryReason,
                lastErrorMessage = lastErrorMessage
            )
            val envelope = DomainEventEnvelopeFactory.envelopeFor(
                data = paymentRetryRequestEvent,
                eventMetaData = EventMetadatas.PaymentOrderRetryRequestedMetadata,
                aggregateId = paymentRetryRequestEvent.publicPaymentOrderId,
                traceId = LogContext.getTraceId() ?: UUID.randomUUID().toString(),
                parentEventId = LogContext.getEventId()
            )
            val eventMapEnd = System.currentTimeMillis()

            val serializationStart = System.currentTimeMillis()
            val json = objectMapper.writeValueAsString(envelope)
            val serializationEnd = System.currentTimeMillis()

            val redisStart = System.currentTimeMillis()
            paymentRetryRedisCache.scheduleRetry(json, retryAt.toDouble())
            val redisEnd = System.currentTimeMillis()

            val totalEnd = System.currentTimeMillis()
            logger.info(
                "TIMING: scheduleRetry " +
                        "| retryCount: ${retryCountEnd - retryCountStart} ms " +
                        "| eventMap: ${eventMapEnd - eventMapStart} ms " +
                        "| serialization: ${serializationEnd - serializationStart} ms " +
                        "| redis: ${redisEnd - redisStart} ms " +
                        "| total: ${totalEnd - totalStart} ms " +
                        "| paymentOrderId=${paymentOrder.paymentOrderId} retryAt=$retryAt"
            )
        } catch (e: Exception) {
            logger.error("❌ Exception during scheduleRetry for paymentOrderId=${paymentOrder.paymentOrderId}", e)
            throw e
        }
    }

    override fun pollDueRetries(maxBatchSize: Long): List<EventEnvelope<PaymentOrderRetryRequested>> {
        val totalStart = System.currentTimeMillis()
        val dueItems = paymentRetryRedisCache.pollDueRetriesAtomic(maxBatchSize)
        val pollEnd = System.currentTimeMillis()
        val dueEnvelopes = dueItems.mapNotNull { json ->
            try {
                val deserializeStart = System.currentTimeMillis()
                val envelope: EventEnvelope<PaymentOrderRetryRequested> =
                    objectMapper.readValue(json, object : TypeReference<EventEnvelope<PaymentOrderRetryRequested>>() {})
                val deserializeEnd = System.currentTimeMillis()
                logger.debug("TIMING: pollDueRetries | deserialization: ${deserializeEnd - deserializeStart} ms for paymentOrderId=${envelope.data.publicPaymentOrderId}")
                envelope
            } catch (e: Exception) {
                logger.error("❌ Failed to deserialize retry event from Redis", e)
                null
            }
        }
        val totalEnd = System.currentTimeMillis()
        logger.debug(
            "TIMING: pollDueRetries | redis poll: ${pollEnd - totalStart} ms | total: ${totalEnd - totalStart} ms | polled count: ${dueItems.size}"
        )
        return dueEnvelopes
    }

    override fun getRetryCount(paymentOrderId: PaymentOrderId): Int {
        return paymentRetryRedisCache.getRetryCount(paymentOrderId.value)
    }

    override fun resetRetryCounter(paymentOrderId: PaymentOrderId) {
        paymentRetryRedisCache.resetRetryCounter(paymentOrderId.value)
    }
}