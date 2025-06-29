package com.dogancaglar.consumers.base

import com.dogancaglar.common.event.EventEnvelope
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.support.Acknowledgment
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Future

abstract class BaseBatchKafkaConsumer<T : Any> : AbstractKafkaConsumer<T>() {
    abstract fun consume(record: ConsumerRecord<String, EventEnvelope<T>>)
    open fun getExecutor(): ThreadPoolTaskExecutor? = null

    fun handleBatch(records: List<ConsumerRecord<String, EventEnvelope<T>>>, acknowledgment: Acknowledgment) {
        val filtered = records.filter { filter(extractEnvelope(it)) }
        val executor = getExecutor()
        if (executor == null) {
            // Sequential (default)
            filtered.forEach { record ->
                withLogContext(extractEnvelope(record)) {
                    consume(record)
                }
            }
        } else {
            // Parallel with MDC context
            val futures: List<Future<*>> = filtered.map { record ->
                val envelope = extractEnvelope(record)
                executor.submit {
                    withLogContext(envelope) {
                        consume(record)
                    }
                }
            }
            try {
                futures.forEach { it.get() }
                acknowledgment.acknowledge() // Only if ALL records succeeded!
            } catch (ex: Exception) {
                LoggerFactory.getLogger(javaClass).error("Batch failed, will NOT ACK: ", ex)
                throw RuntimeException("Batch failed, not ack", ex)
                // No ack = batch reprocessed (all-or-nothing)
            }
        }
    }
}
