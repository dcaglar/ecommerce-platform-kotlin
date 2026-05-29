package com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.config

import com.dogancaglar.common.event.EventEnvelope
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.core.KafkaTemplate

class KafkaTxExecutor(
    private val kafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>
) {
    fun <R> run(
        offsets: Map<TopicPartition, OffsetAndMetadata>,
        groupMeta: ConsumerGroupMetadata,
        block: () -> R
    ): R =
        kafkaTemplate.executeInTransaction { ops ->
            val result = block()
            ops.sendOffsetsToTransaction(offsets, groupMeta)
            result as (R & Any)
        }


    fun <R> run(block: () -> R): R =
        kafkaTemplate.executeInTransaction { block() as (R & Any) }
}