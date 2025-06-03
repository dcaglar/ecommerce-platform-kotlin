package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.mapper.PaymentOrderEventMapper
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component


@Component("paymentRetryPaymentAdapter")
open class PaymentRetryPaymentAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) : RetryQueuePort<PaymentOrderRetryRequested> {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val queue = "payment_retry_queue"

    override fun scheduleRetry(
        paymentOrder: PaymentOrder,
        backOffMillis: Long
    ) {
        val paymentOrderRetryRequested = PaymentOrderEventMapper.toPaymentOrderRetryRequestEvent(order = paymentOrder)
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = paymentOrderRetryRequested,
            eventMetaData = EventMetadatas.PaymentOrderRetryRequestedMetadata,
            aggregateId = paymentOrderRetryRequested.publicPaymentOrderId,
            traceId = LogContext.getTraceId()!!,
            parentEventId = LogContext.getEventId()
        )
        LogContext.with(envelope= envelope,additionalContext = mapOf<String,String>(
            "retryCount" to paymentOrderRetryRequested.retryCount.toString(),
            "retryDelay" to paymentOrderRetryRequested.   "retryReason" to "PSP_TIMEOUT"
        )) {
        {   logger.info("Sending to redis with expantoal backoff jittery $")
            val json = objectMapper.writeValueAsString(envelope);
            val retryAt = System.currentTimeMillis() + backOffMillis
            redisTemplate.opsForZSet().add(queue, json, retryAt.toDouble())
        }
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
}

