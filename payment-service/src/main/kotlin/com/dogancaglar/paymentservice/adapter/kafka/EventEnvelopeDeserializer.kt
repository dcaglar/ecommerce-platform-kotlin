package com.dogancaglar.paymentservice.adapter.kafka

import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.PaymentOrderRetryRequested


import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusCheckRequested
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.Deserializer

class EventEnvelopeDeserializer : Deserializer<EventEnvelope<*>> {

    private val objectMapper = ObjectMapper()

    private val topicTypeMap = mapOf(
        "payment_order_created" to object : TypeReference<EventEnvelope<PaymentOrderCreated>>() {},
        "payment_order_retry_request_topic" to object : TypeReference<EventEnvelope<PaymentOrderRetryRequested>>() {},
        "delay_scheduling_topic" to object : TypeReference<EventEnvelope<PaymentOrderStatusCheckRequested>>() {},
        )
    /*
    dynamic-consumers:
    - id: payment-retry-executor
      topic: payment_order_retry_request_topic
      group-id: payment-retry-executor-group
      class-name: com.dogancaglar.paymentservice.adapter.kafka.PaymentRetryExecutor
    - id: payment-order-executor
      topic: payment_order_created
      group-id: payment-order-group
      class-name: com.dogancaglar.paymentservice.adapter.kafka.PaymentOrderExecutor
    - id: payment-status-executor
      topic: delay_scheduling_topic
      group-id: payment-status-executor-group
      class-name: com.dogancaglar.paymentservice.adapter.kafka.PaymentStatusDelayedQueueExecutor
payment-service:
     */

    override fun deserialize(topic: String?, data: ByteArray?): EventEnvelope<*>? {
        return deserialize(topic, null, data)
    }

    override fun deserialize(topic: String?, headers: Headers?, data: ByteArray?): EventEnvelope<*>? {
        if (data == null || data.isEmpty()) return null

        val typeRef = topicTypeMap[topic]
            ?: throw IllegalArgumentException("No type mapping found for topic: $topic")

        return objectMapper.readValue(data, typeRef)
    }

    override fun close() {
        // no-op
    }
}