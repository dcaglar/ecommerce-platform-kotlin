package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.CaptureSuccessful
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxWriterPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpServletRequest
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext

data class AdyenWebhookPayload(
    val eventCode: String,
    val success: String,
    val originalReference: String,
    val merchantAccountCode: String,
    val amount: AmountPayload
)

data class AmountPayload(
    val value: Long,
    val currency: String
)

@RestController
@RequestMapping("/api/v1/webhooks")
class AdyenWebhookController(
    private val outboxWriterPort: LocalOutboxWriterPort,
    private val idGeneratorPort: IdGeneratorPort,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/adyen")
    fun handleAdyenWebhook(
        request: HttpServletRequest,
        @RequestBody payload: AdyenWebhookPayload
    ): ResponseEntity<String> {
        logger.info("📥 Received Adyen webhook: eventCode=\${payload.eventCode}, success=\${payload.success}, originalReference=\${payload.originalReference}")

        // TODO: For now I'm not gonna implement a real verification, just a stub
        // verifyHmacSignature(request, payload)

        if (payload.eventCode == "CAPTURE" && payload.success == "true") {
            val captureSuccessful = CaptureSuccessful.from(
                paymentIntentId = "", // internal ID not known at edge easily
                publicPaymentIntentId = payload.originalReference,
                merchantAccountId = payload.merchantAccountCode,
                amountValue = payload.amount.value,
                currency = payload.amount.currency,
                now = Utc.nowInstant()
            )

            val envelope = EventEnvelopeFactory.envelopeFor(
                traceId = EventLogContext.getTraceId(),
                data = captureSuccessful,
                aggregateId = captureSuccessful.publicPaymentIntentId,
                parentEventId = EventLogContext.getEventId()
            )

            val outboxEvent = OutboxEvent.createNew(
                oeid = idGeneratorPort.nextPaymentId(),
                eventType = envelope.eventType,
                aggregateId = envelope.aggregateId,
                payload = objectMapper.writeValueAsString(envelope)
            )

            outboxWriterPort.save(outboxEvent)
            logger.info("Appended CaptureSuccessful to local outbox for payment \${payload.originalReference}")
        }

        return ResponseEntity.ok("[accepted]")
    }
}
