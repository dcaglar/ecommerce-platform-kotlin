package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.metadata.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.application.util.RetryItem
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.*

@Component("paymentOrderRetryQueueAdapter")
class PaymentOrderRetryQueueAdapter(
    private val paymentOrderRetryRedisCache: PaymentOrderRetryRedisCache,
    meterRegistry: MeterRegistry,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper,
    val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
) : RetryQueuePort<PaymentOrderCaptureCommand> {

    init {
        Gauge.builder("redis_retry_zset_size") { paymentOrderRetryRedisCache.zsetSize() }
            .description("Number of entries pending in the Redis retry ZSet")
            .register(meterRegistry)
    }

    private val logger = LoggerFactory.getLogger(PaymentOrderRetryQueueAdapter::class.java)

    override fun scheduleRetry(
        paymentOrder: PaymentOrder,
        backOffMillis: Long
    ) {
        val totalStart = System.currentTimeMillis()
        try {
            val retryAt = System.currentTimeMillis() + backOffMillis

            // Build PSP_CALL_REQUESTED with DB-owned attempt
            val pspCallRequested = paymentOrderDomainEventMapper.toPaymentOrderCaptureCommand(
                order = paymentOrder,
                attempt = paymentOrder.retryCount
            )
            val envelope = DomainEventEnvelopeFactory.envelopeFor(
                data = pspCallRequested,
                eventMetaData = EventMetadatas.PaymentOrderCaptureCommandMetadata,
                aggregateId = pspCallRequested.paymentOrderId,
                traceId = LogContext.getTraceId() ?: UUID.randomUUID().toString(),
                parentEventId = LogContext.getEventId()
            )

            val serializationStart = System.currentTimeMillis()
            val json = objectMapper.writeValueAsString(envelope)
            val serializationEnd = System.currentTimeMillis()

            val redisStart = System.currentTimeMillis()
            paymentOrderRetryRedisCache.scheduleRetry(json, retryAt.toDouble())
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
            logger.error("❌ Exception during scheduleRetry for agg={}", paymentOrder.paymentOrderId.toPublicPaymentOrderId(), e)
            throw e
        }
    }

    // Optional ops helpers
    override fun getRetryCount(paymentOrderId: PaymentOrderId): Int =
        paymentOrderRetryRedisCache.getRetryCount(paymentOrderId.value)

    override fun resetRetryCounter(paymentOrderId: PaymentOrderId) {
        paymentOrderRetryRedisCache.resetRetryCounter(paymentOrderId.value)
    }


    /** New: pop to inflight and return [RetryItem]s. */
    override fun pollDueRetriesToInflight(maxBatchSize: Long): List<RetryItem> {
        val raws: List<ByteArray> = paymentOrderRetryRedisCache.popDueToInflight(maxBatchSize)
        if (raws.isEmpty()) return emptyList()
        val items = mutableListOf<RetryItem>()
        for (raw in raws) {
            try {
                val env: EventEnvelope<PaymentOrderCaptureCommand> =
                    objectMapper.readValue(raw, object : TypeReference<EventEnvelope<PaymentOrderCaptureCommand>>() {})
                items += RetryItem(env, raw)
            } catch (e: Exception) {
                // If we cannot deserialize, drop from inflight to avoid poison loops
                paymentOrderRetryRedisCache.removeFromInflight(raw)
            }
        }
        return items
    }


    fun removeFromInflight(raw: ByteArray) =
        paymentOrderRetryRedisCache.removeFromInflight(raw)

    fun reclaimInflight(olderThanMs: Long = 60_000) =
        paymentOrderRetryRedisCache.reclaimInflight(olderThanMs)
}
