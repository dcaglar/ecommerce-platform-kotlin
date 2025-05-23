package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.ScheduledPaymentOrderStatusRequest
import com.dogancaglar.paymentservice.domain.event.toPaymentOrderStatusScheduled
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.EventPublisherPort
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component("paymentRetryStatusAdapter")
open class PaymentRetryStatusAdapter(private val redisTemplate: StringRedisTemplate,
                                     @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper,
                                    val paymentEventPublisher: EventPublisherPort ) : RetryQueuePort<ScheduledPaymentOrderStatusRequest> {
    private val queue = "payment_status_queue"
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun scheduleRetry( paymentOrder: PaymentOrder) {
        val paymentOrderStatusScheduled = paymentOrder.toPaymentOrderStatusScheduled()
        val envelope = EventEnvelope.wrap(
           eventType = EventMetadatas.PaymentOrderStatusCheckScheduledMetadata.eventType,
            aggregateId = paymentOrderStatusScheduled.paymentOrderId,
            data = paymentOrderStatusScheduled,
            traceId = MDC.get(LogFields.TRACE_ID) // optional
        )
        val json = objectMapper.writeValueAsString(envelope);
        //todo publish a PaymentOrderStatusCheckScheduledMetadata
        redisTemplate.opsForList().leftPush(queue, json)
    }



    override fun pollDueRetries(): List<EventEnvelope<ScheduledPaymentOrderStatusRequest>> {
        val envelopes = mutableListOf<EventEnvelope<ScheduledPaymentOrderStatusRequest>>()

        repeat(50) {
            val json = redisTemplate.opsForList().rightPop(queue)
            if (json != null) {
                try {
                    val envelope: EventEnvelope<ScheduledPaymentOrderStatusRequest> =
                        objectMapper.readValue(json, object : TypeReference<EventEnvelope<ScheduledPaymentOrderStatusRequest>>() {})
                    envelopes.add(envelope)
                } catch (e: Exception) {
                    logger.warn("❌ Failed to parse ScheduledPaymentOrderStatusRequest envelope: ${e.message}")
                }
            } else {
                return@repeat // no more items
            }
        }

        return envelopes
    }
}