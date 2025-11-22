package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ORDER_ID
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.ports.inbound.RequestLedgerRecordingUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
@Component
class LedgerRecordingRequestDispatcher(
    @param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
    private val requestLedgerRecordingUseCase: RequestLedgerRecordingUseCase,
    private val dedupe: EventDeduplicationPort,

    ) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_FINALIZED],
        containerFactory = "${Topics.PAYMENT_ORDER_FINALIZED}-factory",
        groupId = CONSUMER_GROUPS.LEDGER_RECORDING_REQUEST_DISPATCHER
    )
    fun onPaymentOrderFinalized(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderFinalized>>,
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

            kafkaTx.run(offsets, groupMeta) {

                requestLedgerRecordingUseCase.requestLedgerRecording(eventData)
                dedupe.markProcessed(envelope.eventId, 3600)
            }
            logger.info(
                "✅ Completed processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                        "with $PAYMENT_ID ${eventData.publicPaymentId}")
        }
    }
}