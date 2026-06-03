package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.consumers


import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.CaptureReceived
import com.dogancaglar.paymentservice.application.events.ExternalAsyncCaptureToPspPerformed
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.payment.PspModificationStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxWriterPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspModificationGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

@Component
class CaptureCommandExecutor(
    private val objectMapper: ObjectMapper,
    private val outboxWriterPort: LocalOutboxWriterPort,
    private val idGeneratorPort: IdgeneratorPort,
    private val pspModificationGatewayPort: PspModificationGatewayPort,
    private val paymentIntentRepository: PaymentIntentRepository,
    private val retryQueuePort: RetryQueuePort<CaptureReceived>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val MAX_RETRIES = 5
    }

    @KafkaListener(
        topics = [Topics.CAPTURE_EXECUTION_QUEUE],
        groupId = CONSUMER_GROUPS.CAPTURE_COMMAND_EXECUTOR
    )
    fun consume(payload: String) {
        val typeRef = object : TypeReference<EventEnvelope<CaptureReceived>>() {}
        val envelope = objectMapper.readValue(payload, typeRef)
        val event = envelope.data

        EventLogContext.with(envelope) {
            logger.info("Executing network capture for payment: \${event.publicPaymentIntentId}")

            try {
                val paymentIntent = paymentIntentRepository.findById(PaymentIntentId(event.paymentIntentId.toLong()))

                // 1. Simulate external network call to PSP (e.g. Adyen)
                val status = pspModificationGatewayPort.capture(paymentIntent)

                if (status.isRetryablePspResponse()) {
                    logger.warn("PSP returned retryable status: $status. Triggering retry for payment: \${event.publicPaymentIntentId}")
                    handleRetry(event, "PSP returned $status")
                    return@with // Stop processing, do not append to outbox
                }

                if (status.isTerminalPspResponse() && status != PspModificationStatus.CAPTURED) {
                    logger.error("PSP returned terminal failure: $status. Payment: \${event.publicPaymentIntentId}")
                    // Handle terminal failure (e.g., mark as failed in DB or emit failed event)
                    // For now, we return and don't append to outbox.
                    return@with
                }

                // 2. Append ExternalAsyncCaptureToPspPerformed to Central Outbox
                val performedEvent = ExternalAsyncCaptureToPspPerformed.Companion.from(
                    captureTxId = event.captureTxId,
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

                val outboxEvent = OutboxEvent.Companion.createNew(
                    oeid = idGeneratorPort.nextPaymentId(),
                    eventType = outboxEnvelope.eventType,
                    aggregateId = outboxEnvelope.aggregateId,
                    payload = objectMapper.writeValueAsString(outboxEnvelope)
                )

                outboxWriterPort.save(outboxEvent)

                logger.info("Capture network command executed successfully, appended to central outbox.")

            } catch (e: Exception) {
                logger.error("❌ Exception during PSP capture call for payment: \${event.publicPaymentIntentId}", e)
                handleRetry(event, e.message)
            }
        }
    }

    private fun handleRetry(event: CaptureReceived, lastError: String?) {
        val nextAttempt = event.attempt + 1
        if (nextAttempt > MAX_RETRIES) {
            logger.warn(
                "[RETRY-FAILURE] paymentIntentId={} has reached the maximum retry attempts ({}/{}). Marking as permanently FAILED. LastError='{}'",
                event.publicPaymentIntentId,
                nextAttempt,
                MAX_RETRIES,
                lastError ?: "-"
            )
            // TODO: Optional - append CaptureFailed to Outbox or mark PaymentIntent as FAILED in DB
            return
        }

        val backoffMs = computeEqualJitterBackoff(nextAttempt)
        val scheduledAt = Utc.nowInstant().plusMillis(backoffMs).toEpochMilli()

        val retryEvent = event.withIncrementedAttempt()

        logRetrySchedule(retryEvent, nextAttempt, scheduledAt, lastError)
        retryQueuePort.scheduleRetry(retryEvent, backoffMs)
    }

    private fun logRetrySchedule(
        event: CaptureReceived,
        nextRetryCount: Int,
        scheduledAt: Long,
        lastError: String?
    ) {
        val scheduledInstant = Instant.ofEpochMilli(scheduledAt)
        val amsterdamZone = ZoneId.of("Europe/Amsterdam")
        val scheduledLocal = LocalDateTime.ofInstant(scheduledInstant, amsterdamZone)
        val formattedLocal = scheduledLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

        logger.info(
            "Scheduling retry for paymentIntentId={} [attempt {}/{}] due at {} (Amsterdam time). LastError='{}'",
            event.publicPaymentIntentId,
            nextRetryCount,
            MAX_RETRIES,
            formattedLocal,
            lastError ?: ""
        )
    }

    private fun computeEqualJitterBackoff(attempt: Int, minDelayMs: Long = 2000L, maxDelayMs: Long = 60000L): Long {
        val exp = (minDelayMs * 2.0.pow((attempt - 1).coerceAtLeast(0))).toLong()
        val capped = min(exp, maxDelayMs)
        return capped / 2 + Random.Default.nextLong(capped / 2 + 1)
    }
}