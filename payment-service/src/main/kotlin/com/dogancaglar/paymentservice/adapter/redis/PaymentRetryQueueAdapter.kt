package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.mapper.PaymentOrderEventMapper
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.*

@Component("paymentRetryQueueAdapter")
class PaymentRetryQueueAdapter(
    val paymentRetryRedisCache: PaymentRetryRedisCache,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) :
    RetryQueuePort<PaymentOrderRetryRequested> {
    override fun scheduleRetry(
        paymentOrder: PaymentOrder,
        backOffMillis: Long,
        retryReason: String?,
        lastErrorMessage: String?
    ) {
        val retryCount = paymentRetryRedisCache.incrementAndGetRetryCount(paymentOrder.paymentOrderId)
        val retryAt = System.currentTimeMillis() + backOffMillis
        val paymentRetryRequestEvent = PaymentOrderEventMapper.toPaymentOrderRetryRequestEvent(
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
        val json = objectMapper.writeValueAsString(envelope)
        paymentRetryRedisCache.scheduleRetry(json, retryAt.toDouble())
    }


    override fun pollDueRetries(): List<EventEnvelope<PaymentOrderRetryRequested>> {
        val dueItems = paymentRetryRedisCache.pollDueRetries()
        val dueEnvelops = dueItems.mapNotNull { json ->
            try {
                // Deserializing the full EventEnvelope
                val envelope: EventEnvelope<PaymentOrderRetryRequested> =
                    objectMapper.readValue(json, object : TypeReference<EventEnvelope<PaymentOrderRetryRequested>>() {})
                envelope // You return only the domain event
            } catch (e: Exception) {
                // Optionally log and skip corrupted entries
                null
            }
        }
        return dueEnvelops
    }

    override fun getRetryCount(paymentOrderId: Long): Int {
        return paymentRetryRedisCache.getRetryCount(paymentOrderId)
    }

    override fun resetRetryCounter(paymentOrderId: Long) {
        paymentRetryRedisCache.resetRetryCounter(paymentOrderId)
    }
}