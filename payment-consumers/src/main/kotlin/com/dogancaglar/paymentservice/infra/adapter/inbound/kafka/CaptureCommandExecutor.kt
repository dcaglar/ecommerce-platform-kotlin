package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.events.CaptureReceived
import com.dogancaglar.paymentservice.application.events.ExternalAsyncCaptureToPspPerformed
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxWriterPort
import com.dogancaglar.common.time.Utc
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import com.dogancaglar.common.event.EventEnvelopeFactory

@Component
class CaptureCommandExecutor(
    private val objectMapper: ObjectMapper,
    private val outboxWriterPort: LocalOutboxWriterPort,
    private val idGeneratorPort: IdGeneratorPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.CAPTURE_EXECUTION_QUEUE],
        groupId = CONSUMER_GROUPS.CAPTURE_COMMAND_EXECUTOR
    )
    fun consume(payload: String) {
        val typeRef = object : TypeReference<EventEnvelope<CaptureReceived>>() {}
        val envelope = objectMapper.readValue(payload, typeRef)
        val event = envelope.data

        logger.info("Executing network capture for payment: \${event.publicPaymentIntentId}")

        // 1. Simulate external network call to PSP (e.g. Adyen)
        // val response = pspCaptureGateway.capture(amount, merchantAccountId, ...)

        // 2. Append ExternalAsyncCaptureToPspPerformed to Central Outbox
        val performedEvent = ExternalAsyncCaptureToPspPerformed.from(
            paymentIntentId = event.paymentIntentId,
            publicPaymentIntentId = event.publicPaymentIntentId,
            merchantAccountId = event.merchantAccountId,
            amountValue = event.amountValue,
            currency = event.currency,
            now = Utc.nowInstant()
        )

        val outboxEnvelope = EventEnvelopeFactory.envelopeFor(
            traceId = envelope.traceId, // Keep traceId for observability
            data = performedEvent,
            aggregateId = performedEvent.publicPaymentIntentId,
            parentEventId = envelope.eventId // Link to the original CaptureReceived event
        )

        val outboxEvent = OutboxEvent.createNew(
            oeid = idGeneratorPort.nextPaymentId(),
            eventType = outboxEnvelope.eventType,
            aggregateId = outboxEnvelope.aggregateId,
            payload = objectMapper.writeValueAsString(outboxEnvelope)
        )

        outboxWriterPort.save(outboxEvent)
        
        logger.info("Capture network command executed successfully, appended to central outbox.")
    }
}
