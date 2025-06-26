package com.dogancaglar.paymentservice.adapter.kafka.base

import com.dogancaglar.common.event.EventEnvelope
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.support.Acknowledgment

abstract class BaseBatchKafkaConsumer<T : Any> : AbstractKafkaConsumer<T>() {
    abstract fun consume(record: ConsumerRecord<String, EventEnvelope<T>>)
    fun handleBatch(records: List<ConsumerRecord<String, EventEnvelope<T>>>, acknowledgment: Acknowledgment) {
        val filtered = records.filter { filter(extractEnvelope(it)) }
        filtered.forEach { record ->
            withLogContext(extractEnvelope(record)) {
                consume(record)
            }
        }
    }
}