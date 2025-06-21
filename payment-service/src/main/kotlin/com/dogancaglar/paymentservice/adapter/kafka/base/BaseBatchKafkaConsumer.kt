package com.dogancaglar.paymentservice.adapter.kafka.base

import com.dogancaglar.common.event.EventEnvelope
import org.apache.kafka.clients.consumer.ConsumerRecord

abstract class BaseBatchKafkaConsumer<T : Any> : AbstractKafkaConsumer<T>() {
    abstract fun consume(records: List<ConsumerRecord<String, EventEnvelope<T>>>)

    fun handleBatch(records: List<ConsumerRecord<String, EventEnvelope<T>>>) {
        val filtered = records.filter { filter(extractEnvelope(it)) }
        if (filtered.isNotEmpty()) {
            withLogContext(extractEnvelope(filtered.first())) {
                consume(filtered)
            }
        }
    }
}