package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.ports.inbound.RecordLedgerEntriesUseCase
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class LedgerRecordingConsumer(
    @param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
    private val recordLedgerEntriesUseCase: RecordLedgerEntriesUseCase
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.LEDGER_RECORD_REQUEST_QUEUE],
        containerFactory = "${Topics.LEDGER_RECORD_REQUEST_QUEUE}-factory",
        groupId = CONSUMER_GROUPS.LEDGER_RECORDING_CONSUMER
    )
    fun onLedgerRequested(
        record: ConsumerRecord<String, EventEnvelope<LedgerRecordingCommand>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val env = record.value()
        val command = env.data

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()

        LogContext.with(env) {
            kafkaTx.run(offsets, groupMeta) {
                logger.info(
                    "🧾 Recording ledger entries for paymentOrderId={} status={} traceId={}",
                    command.publicPaymentOrderId, command.status, env.traceId
                )

                recordLedgerEntriesUseCase.recordLedgerEntries(command)

                logger.info(
                    "✅ Ledger recording complete and event published for paymentOrderId={}",
                    command.publicPaymentOrderId
                )
            }
        }
    }
}