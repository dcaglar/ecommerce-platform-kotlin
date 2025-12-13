package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ORDER_ID
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.ports.inbound.RecordLedgerEntriesUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
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
    private val recordLedgerEntriesUseCase: RecordLedgerEntriesUseCase,
    private val dedupe: EventDeduplicationPort
    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.LEDGER_RECORD_REQUEST_QUEUE],
        containerFactory = "${Topics.LEDGER_RECORD_REQUEST_QUEUE}factory",
        groupId = CONSUMER_GROUPS.LEDGER_RECORDING_CONSUMER
    )
    fun onLedgerRequested(
        record: ConsumerRecord<String, EventEnvelope<LedgerRecordingCommand>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val envelope = record.value()
        val eventData = envelope.data

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()

        EventLogContext.with(envelope) {
            if (dedupe.exists(envelope.eventId)) {
                logger.warn(
                    "⚠️ Event is processed already  for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId} , skipping")
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }
            logger.info(
                "🎬 Started processing   authorization event for $PAYMENT_ID  ${eventData.publicPaymentId}")
            try {
                kafkaTx.run(offsets, groupMeta) {
                    logger.info(
                        "🧾 Recording ledger entries for paymentOrderId={} status={} traceId={}",
                        eventData.paymentOrderId, eventData.finalStatus, envelope.traceId
                    )
                    recordLedgerEntriesUseCase.recordLedgerEntries(eventData)
                    dedupe.markProcessed(envelope.eventId, 3600)
                    logger.info(
                        "✅ Ledger recording complete and event published for paymentOrderId={}",
                        eventData.paymentOrderId
                    )
                }
            } catch (e: Exception) {
                logger.error(
                    "❌ Failed to record ledger entries for paymentOrderId={} status={} traceId={}: {}",
                    eventData.paymentOrderId, eventData.finalStatus, envelope.traceId, e.message, e
                )
                throw e // Re-throw to let Spring Kafka error handler process it
            }
            logger.info(
                "✅ Completed processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                        "with $PAYMENT_ID ${eventData.publicPaymentId}")
        }
    }
}