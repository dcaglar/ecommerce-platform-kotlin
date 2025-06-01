package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.mapper.PaymentOrderEventMapper
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID


@Component("paymentRetryPaymentAdapter")
open class PaymentRetryPaymentAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) : RetryQueuePort<PaymentOrderRetryRequested> {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val queue = "payment_retry_queue"


    override fun scheduleRetry(paymentOrder: PaymentOrder) {
        val paymentOrderRetryRequested = PaymentOrderEventMapper.toPaymentOrderRetryRequestEvent(order = paymentOrder)
        val envelope = DomainEventEnvelopeFactory.envelopeFor(data = paymentOrderRetryRequested,
            eventType = EventMetadatas.PaymentOrderRetryRequestedMetadata,
            aggregateId = paymentOrderRetryRequested.publicPaymentOrderId,
            traceId = MDC.get(LogFields.TRACE_ID) ?: UUID.randomUUID().toString(),
            )
        val json = objectMapper.writeValueAsString(envelope);
        val delayMillis = calculateBackoffMillis(paymentOrder.retryCount)
        val retryAt = System.currentTimeMillis() + delayMillis
        redisTemplate.opsForZSet().add(queue, json, retryAt.toDouble())
    }

    override fun pollDueRetries(): List<EventEnvelope<PaymentOrderRetryRequested>> {
        val now = System.currentTimeMillis().toDouble()
        val dueItems = redisTemplate.opsForZSet().rangeByScore(queue, 0.0, now)

        val dueEnvelops = dueItems?.mapNotNull { json ->
            try {
                // Deserializing the full EventEnvelope
                val envelope: EventEnvelope<PaymentOrderRetryRequested> =
                    objectMapper.readValue(json, object : TypeReference<EventEnvelope<PaymentOrderRetryRequested>>() {})
                redisTemplate.opsForZSet().remove(queue, json)
                envelope // You return only the domain event
            } catch (e: Exception) {
                // Optionally log and skip corrupted entries
                null
            }
        } ?: emptyList()
        return dueEnvelops
    }


    fun calculateBackoffMillis(retryCount: Int): Long {
        val baseDelay = 5_000L // 5 seconds
        return baseDelay * (retryCount + 1) // Linear or exponential backoff
    }
}