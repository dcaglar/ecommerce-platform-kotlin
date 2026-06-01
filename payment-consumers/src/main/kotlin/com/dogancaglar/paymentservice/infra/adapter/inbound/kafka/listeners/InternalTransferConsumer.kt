package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.listeners

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.events.InternalTransferRequest
import com.dogancaglar.paymentservice.application.service.PspResultProcessingService
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.apache.kafka.clients.consumer.Consumer

/**
 * InternalTransferConsumer
 *
 * Mandate: Listens to internal-transfer-queue and delegates to PspResultProcessingService
 * to execute real financial state mutations (INTERNAL_TRANSFER double-entry ledger postings).
 */
@Component
class InternalTransferConsumer(
    private val pspResultProcessingService: PspResultProcessingService,
    private val dedupe: EventDeduplicationPort,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.INTERNAL_TRANSFER_QUEUE],
        groupId = CONSUMER_GROUPS.INTERNAL_TRANSFER_CONSUMER
    )
    fun onInternalTransferRequest(
        record: ConsumerRecord<String, String>,
        consumer: Consumer<*, *>
    ) {
        val typeRef = object : TypeReference<EventEnvelope<InternalTransferRequest>>() {}
        val envelope = try {
            objectMapper.readValue(record.value(), typeRef)
        } catch (e: Exception) {
            logger.error("Failed to deserialize InternalTransferRequest: \${record.value()}", e)
            return // Or throw to DLQ
        }

        val eventId = envelope.eventId
        val exists = dedupe.exists(eventId)
        if (exists) {
            logger.warn("⚠️ Event is processed already, skipping eventId=\$eventId")
            return
        }

        val event = envelope.data
        logger.info("🎬 Processing InternalTransferRequest event for paymentIntentId: \${event.publicPaymentIntentId}, target: \${event.targetAccountId}")

        try {
            pspResultProcessingService.processInternalTransferRequest(event)
            dedupe.markProcessed(eventId, 3600)
            consumer.commitSync() // Manual ack
        } catch (e: Exception) {
            logger.error("❌ Failed to process InternalTransferRequest for paymentIntentId: \${event.publicPaymentIntentId}", e)
            throw e // Let Kafka handle retry/DLQ
        }
    }
}
