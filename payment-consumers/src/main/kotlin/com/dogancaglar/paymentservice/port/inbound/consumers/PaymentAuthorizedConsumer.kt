package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ORDER_ID
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.commands.LedgerRecordingAuthorizationCommand
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentIntentAuthorized
import com.dogancaglar.paymentservice.application.usecases.RecordAuthorizationLedgerEntriesService
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentAuthorizedConsumer(
    @param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
    private val dedupe: EventDeduplicationPort,
    private val recordAuthorizationLedgerEntriesService: RecordAuthorizationLedgerEntriesService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_AUTHORIZED],
        containerFactory = "${Topics.PAYMENT_AUTHORIZED}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_AUTHORIZED_CONSUMER
    )
    fun onCreated(
        record: ConsumerRecord<String, EventEnvelope<PaymentAuthorized>>,
        consumer: Consumer<*, *>
    ) {
        val envelope = record.value()
        val eventData = envelope.data

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()
        EventLogContext.with(envelope) {
            if (dedupe.exists(envelope.eventId)) {
                logger.warn(
                    "⚠️ Event is processed already  for $PAYMENT_ID  ${eventData.publicPaymentId} " +
                          " , skipping")
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }
            logger.info(
                "🎬 Started processing   authorization event for $PAYMENT_ID  ${eventData.publicPaymentId}")
            try {
                kafkaTx.run(offsets, groupMeta) {
                    logger.info(
                        "🧾 Recording ledger authorization entries for paymentId=${eventData.publicPaymentId} traceid:${envelope.traceId}",
                    )
                    recordAuthorizationLedgerEntriesService.recordAuthorization(LedgerRecordingAuthorizationCommand.from(eventData,Utc.nowInstant()))
                    dedupe.markProcessed(envelope.eventId, 3600)
                    logger.info(
                        "✅ Ledger recording authorization  complete and event published for paymenId=${eventData.paymentId}"
                    )
                }
            } catch (e: Exception) {
                logger.error(
                    "❌ Failed to record authorization ledger entries for paymentId=${eventData.paymentId} traceId=${envelope.traceId}"
                )
                throw e // Re-throw to let Spring Kafka error handler process it
            }
            logger.info(
                "🎬 Completed processing   authorization event for $PAYMENT_ID  ${eventData.publicPaymentId}")
        }

    }
}