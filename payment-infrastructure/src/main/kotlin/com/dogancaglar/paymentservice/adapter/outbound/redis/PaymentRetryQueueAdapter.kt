package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.RetryItem
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
    private val paymentRetryRedisCache: PaymentRetryRedisCache,
    meterRegistry: MeterRegistry,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) : RetryQueuePort<PaymentOrderPspCallRequested> {

    init {
        Gauge.builder("redis_retry_zset_size") { paymentRetryRedisCache.zsetSize() }
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
            val retryAt = System.currentTimeMillis() + backOffMillis

            // Build PSP_CALL_REQUESTED with DB-owned attempt
            val pspCallRequested = PaymentOrderDomainEventMapper.toPaymentOrderPspCallRequested(
                order = paymentOrder,
                attempt = paymentOrder.retryCount
            )
            val envelope = DomainEventEnvelopeFactory.envelopeFor(
                data = pspCallRequested,
                eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
                aggregateId = pspCallRequested.paymentOrderId,
                traceId = LogContext.getTraceId() ?: UUID.randomUUID().toString(),
                parentEventId = LogContext.getEventId()
            )

            val serializationStart = System.currentTimeMillis()
            val json = objectMapper.writeValueAsString(envelope)
            val serializationEnd = System.currentTimeMillis()

            val redisStart = System.currentTimeMillis()
            paymentRetryRedisCache.scheduleRetry(json, retryAt.toDouble())
            val redisEnd = System.currentTimeMillis()

            val totalEnd = System.currentTimeMillis()
            logger.debug(
                "TIMING: scheduleRetry | serialize: {} ms | redis: {} ms | total: {} ms | agg={} attempt={} retryAt={}",
                (serializationEnd - serializationStart),
                (redisEnd - redisStart),
                (totalEnd - totalStart),
                envelope.aggregateId,
                pspCallRequested.retryCount,
                retryAt
            )
        } catch (e: Exception) {
            logger.error("❌ Exception during scheduleRetry for agg={}", paymentOrder.publicPaymentOrderId, e)
            throw e
        }
    }

    // Optional ops helpers
    override fun getRetryCount(paymentOrderId: PaymentOrderId): Int =
        paymentRetryRedisCache.getRetryCount(paymentOrderId.value)

    override fun resetRetryCounter(paymentOrderId: PaymentOrderId) {
        paymentRetryRedisCache.resetRetryCounter(paymentOrderId.value)
    }


    /** New: pop to inflight and return [RetryItem]s. */
    override fun pollDueRetriesToInflight(maxBatchSize: Long): List<RetryItem> {
        val raws: List<ByteArray> = paymentRetryRedisCache.popDueToInflight(maxBatchSize)
        if (raws.isEmpty()) return emptyList()
        val items = mutableListOf<RetryItem>()
        for (raw in raws) {
            try {
                val env: EventEnvelope<PaymentOrderPspCallRequested> =
                    objectMapper.readValue(raw, object : TypeReference<EventEnvelope<PaymentOrderPspCallRequested>>() {})
                items += RetryItem(env, raw)
            } catch (e: Exception) {
                // If we cannot deserialize, drop from inflight to avoid poison loops
                paymentRetryRedisCache.removeFromInflight(raw)
            }
        }
        return items
    }


    fun removeFromInflight(raw: ByteArray) =
        paymentRetryRedisCache.removeFromInflight(raw)

    fun reclaimInflight(olderThanMs: Long = 60_000) =
        paymentRetryRedisCache.reclaimInflight(olderThanMs)
}
