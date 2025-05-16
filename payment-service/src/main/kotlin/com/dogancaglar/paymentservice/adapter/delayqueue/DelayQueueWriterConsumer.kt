
package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.PaymentOrderStatusCheckRequested
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class DelayQueueWriterConsumer(
    private val objectMapper: ObjectMapper,
    private val delayQueueAdapter: JpaDelayQueueAdapter,
) {

    //@KafkaListener(topics = ["delay-scheduling-topic"])
    fun onDelayEvent(record: ConsumerRecord<String, String>) {
        val payload = record.value()
        //paymentORderId
        val key = record.key()

        val envelope = objectMapper.readValue(
            payload,
            object : TypeReference<EventEnvelope<PaymentOrderStatusCheckRequested>>() {}
        )
        val delayMillis = when (val attempt = envelope.data.attempt) {
            1 -> 60_000L  // 1 min
            2 -> 5 * 60_000L  // 5 min
            else -> 15 * 60_000L  // 15 min
        }

        delayQueueAdapter.persist(
            topic = "delay_scheduling_topic",
            key = key,
            payload = payload,
            sendAfterMillis = delayMillis
        )
    }
}

