package com.dogancaglar.paymentservice.infra.adapter.outbound.redis

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventEntityMapper
import com.dogancaglar.paymentservice.application.util.RetryItem
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.infra.adapter.outbound.redis.client.PaymentOrderRetryRedisCache
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("paymentOrderRetryQueueAdapter")
class PaymentOrderRetryQueueAdapter(
    private val paymentOrderRetryRedisCache: PaymentOrderRetryRedisCache,
    meterRegistry: MeterRegistry,
    val serializationPort: SerializationPort) : RetryQueuePort<PaymentOrderCaptureCommand> {

    init {
        Gauge.builder("redis_retry_zset_size") { paymentOrderRetryRedisCache.zsetSize() }
            .description("Number of entries pending in the Redis retry ZSet")
            .register(meterRegistry)
    }

    private val logger = LoggerFactory.getLogger(PaymentOrderRetryQueueAdapter::class.java)

    override fun scheduleRetry(
        paymentOrder: PaymentOrder,
        backOffMillis: Long,
    ) {
        val totalStart = System.currentTimeMillis()
        try {
            val retryAt = System.currentTimeMillis() + backOffMillis

            // Build PSP_CALL_REQUESTED with DB-owned attempt
            val pspCallRequested = PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureCommand(
                order = paymentOrder,
                attempt = paymentOrder.retryCount
            )
            val envelope = EventEnvelopeFactory.envelopeFor(
                data = pspCallRequested,
                aggregateId = pspCallRequested.paymentOrderId,
                traceId = EventLogContext.getTraceId(),
                parentEventId = EventLogContext.getEventId()
            )
            val json = serializationPort.toJson(envelope)
            paymentOrderRetryRedisCache.scheduleRetry(json, retryAt.toDouble())
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
        // Use the new deserialized method - deserialization happens in cache layer (like Kafka)
        return paymentOrderRetryRedisCache.popDueToInflightDeserialized(maxBatchSize)
    }


    fun removeFromInflight(raw: ByteArray) =
        paymentOrderRetryRedisCache.removeFromInflight(raw)

    fun reclaimInflight(olderThanMs: Long = 60_000) =
        paymentOrderRetryRedisCache.reclaimInflight(olderThanMs)
}
