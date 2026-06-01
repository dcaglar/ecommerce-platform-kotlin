package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.events.ExternalAsyncCaptureToPspPerformed
import com.dogancaglar.paymentservice.application.service.PspResultProcessingService
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class CapturePspPerformedConsumer(
    private val objectMapper: ObjectMapper,
    private val pspResultProcessingService: PspResultProcessingService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.CAPTURE_PSP_PERFORMED_QUEUE],
        groupId = CONSUMER_GROUPS.CAPTURE_PSP_PERFORMED
    )
    fun consume(payload: String) {
        val typeRef = object : TypeReference<EventEnvelope<ExternalAsyncCaptureToPspPerformed>>() {}
        val envelope = objectMapper.readValue(payload, typeRef)
        val event = envelope.data

        logger.info("Consuming capture PSP performed event for payment: \${event.publicPaymentIntentId}")

        pspResultProcessingService.processCapturePspPerformed(event)
    }
}
