package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.CaptureRequested
import com.dogancaglar.paymentservice.application.events.CaptureSubmitted
import com.dogancaglar.paymentservice.ports.inbound.usecases.ExecuteCaptureUseCase
import com.dogancaglar.paymentservice.application.service.RecordCaptureSubmissionService
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.payment.PspCaptureGatewayResponse
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxWriterPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspCaptureGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort

@Component
class CaptureCommandExecutor(
    private val executeCaptureUseCase: ExecuteCaptureUseCase,
    private val dedupe: EventDeduplicationPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_RETRIES = 5
        const val GATEWAY_TIMEOUT_MS = 2000L
    }

    @KafkaListener(
        topics = [Topics.CAPTURE_COMMANDS],
        containerFactory = "${Topics.CAPTURE_COMMANDS}-factory",
        groupId = CONSUMER_GROUPS.CAPTURE_COMMAND_EXECUTOR
    )
    fun consume(record: ConsumerRecord<String, EventEnvelope<CaptureRequested>>) {
        val envelope = record.value()
        EventLogContext.with(envelope) {
            val eventId = envelope.eventId
            if (dedupe.exists(eventId)) {
                logger.warn("⚠️ Event is processed already, skipping eventId=\$eventId")
                return@with
            }
            
            val captureRequested = envelope.data
            try {
                executeCaptureUseCase.execute(captureRequested)
                dedupe.markProcessed(eventId, 3600)
            } catch (e: Exception) {
                logger.error("❌ Failed to process capture command for paymentIntentId: \${captureRequested.publicPaymentIntentId}", e)
                throw e
            }
        }
    }
}