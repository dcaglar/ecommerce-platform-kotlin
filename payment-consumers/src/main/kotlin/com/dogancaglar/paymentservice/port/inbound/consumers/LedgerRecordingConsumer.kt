package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.application.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.metadata.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.commands.LedgerRecordingCommand
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
            try {
                kafkaTx.run(offsets, groupMeta) {
                    logger.info(
                        "🧾 Recording ledger entries for paymentOrderId={} status={} traceId={}",
                        command.paymentOrderId, command.status, env.traceId
                    )
                    recordLedgerEntriesUseCase.recordLedgerEntries(command)

                    logger.info(
                        "✅ Ledger recording complete and event published for paymentOrderId={}",
                        command.paymentOrderId
                    )
                }
            } catch (e: Exception) {
                logger.error(
                    "❌ Failed to record ledger entries for paymentOrderId={} status={} traceId={}: {}",
                    command.paymentOrderId, command.status, env.traceId, e.message, e
                )
                throw e // Re-throw to let Spring Kafka error handler process it
            }
        }
    }
}