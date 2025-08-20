// payment-infrastructure module
package com.dogancaglar.paymentservice.config.kafka

import com.dogancaglar.common.event.EventEnvelope
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaTxExecutor(
    @Qualifier("businessEventKafkaTemplate")
    private val kafkaTemplate: KafkaTemplate<String, EventEnvelope<*>>
) {
    fun <R> run(
        offsets: Map<TopicPartition, OffsetAndMetadata>,
        groupMeta: org.apache.kafka.clients.consumer.ConsumerGroupMetadata,
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