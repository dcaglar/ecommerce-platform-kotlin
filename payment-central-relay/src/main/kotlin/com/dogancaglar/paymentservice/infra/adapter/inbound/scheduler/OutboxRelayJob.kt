package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.CaptureReceived
import com.dogancaglar.paymentservice.application.events.ExternalAsyncCaptureToPspPerformed
import com.dogancaglar.paymentservice.application.events.CaptureSuccessful
import com.dogancaglar.paymentservice.application.events.InternalTransferRequest
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded

import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxRelayPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.OutboxEventType
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service

@Service
class OutboxRelayJob(
    private val centralOutboxRepository: CentralOutboxRelayPort,
    @Qualifier("batchPaymentEventPublisher") private val kafkaPublisher: EventPublisherPort,
    @Qualifier("resilientExecutor") private val executor: ThreadPoolTaskExecutor,
    private val objectMapper: ObjectMapper,
    private val serializationPort: SerializationPort,
    @Value("\${outbox-relay.batch-size:500}") private val batchSize: Int,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        Gauge.builder("central_outbox_backlog_size", this) {
            val tSafe = centralOutboxRepository.computeTSafe()
            if (tSafe != null) {
                centralOutboxRepository.countEligible(tSafe).toDouble()
            } else {
                0.0
            }
        }.register(meterRegistry)
    }

    @Scheduled(fixedDelayString = "\${outbox-relay.poll-interval:5000}")
    fun poll() {
        val sample = Timer.start(meterRegistry)
        val tSafe = centralOutboxRepository.computeTSafe()
        if (tSafe == null) {
            logger.debug("No edge watermarks available, skipping poll")
            return
        }

        val batch = centralOutboxRepository.findEligible(tSafe, batchSize)
        if (batch.isEmpty()) {
            return
        }
        
        logger.debug("Fetched {} eligible events (T_safe = {})", batch.size, tSafe)

        // Group events by aggregateId (e.g. sellerId) to preserve ordering per account
        val groups = batch.groupBy { it.aggregateId }

        // Process each group concurrently, but keep events inside each group strictly sequential
        for ((_, events) in groups) {
            executor.execute {
                var chain = CompletableFuture.completedFuture<Void>(null)
                for (entry in events) {
                    chain = chain.thenCompose { processEntryAsync(entry) }
                }
            }
        }
        sample.stop(meterRegistry.timer("relay_poll_duration"))
    }

    private fun processEntryAsync(entry: OutboxEvent): CompletableFuture<Void> {
        val future = try {
            when (OutboxEventType.from(entry.eventType)) {
                OutboxEventType.payment_authorized -> {
                    val envelope = convertToPaymentAuthorizedEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                OutboxEventType.capture_received -> {
                    val envelope = convertToCaptureReceivedEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                OutboxEventType.external_async_capture_psp_performed -> {
                    val envelope = convertToExternalAsyncCaptureToPspPerformedEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                OutboxEventType.capture_successful -> {
                    val envelope = convertToCaptureSuccessfulEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                OutboxEventType.internal_transfer_request -> {
                    val envelope = convertToInternalTransferRequestEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                OutboxEventType.ledger_entries_recorded -> {
                    val envelope = convertToLedgerEntriesRecordedEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                else -> {
                    logger.warn("❓ Unknown outbox event type=${entry.eventType}, skipping oeid=${entry.oeid}")
                    CompletableFuture.completedFuture(null)
                }
            }
        } catch (e: Exception) {
            logger.error("Error starting processing for event oeid=${entry.oeid}", e)
            val failedFuture = CompletableFuture<Void>()
            failedFuture.completeExceptionally(e)
            failedFuture
        }

        return future.handle { _, exception ->
            if (exception == null) {
                centralOutboxRepository.markDispatched(entry.oeid)
                meterRegistry.counter("relay_published_total").increment()
                null
            } else {
                meterRegistry.counter("relay_publish_failed_total").increment()
                logger.error("Failed to publish event oeid=${entry.oeid} for aggregate ${entry.aggregateId}", exception)
                throw exception
            }
        }
    }
     private fun convertToPaymentAuthorizedEnvelope(evt: OutboxEvent):EventEnvelope<PaymentAuthorized> {

        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, PaymentAuthorized::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<PaymentAuthorized>

        return envelope
    }


     private fun convertToCaptureReceivedEnvelope(evt: OutboxEvent): EventEnvelope<CaptureReceived> {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, CaptureReceived::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<CaptureReceived>
        return envelope
    }

     private fun convertToExternalAsyncCaptureToPspPerformedEnvelope(evt: OutboxEvent): EventEnvelope<ExternalAsyncCaptureToPspPerformed> {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, ExternalAsyncCaptureToPspPerformed::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<ExternalAsyncCaptureToPspPerformed>
        return envelope
    }




     private fun convertToCaptureSuccessfulEnvelope(evt: OutboxEvent): EventEnvelope<CaptureSuccessful> {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, CaptureSuccessful::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<CaptureSuccessful>
        return envelope
    }

     private fun convertToInternalTransferRequestEnvelope(evt: OutboxEvent): EventEnvelope<InternalTransferRequest> {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, InternalTransferRequest::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<InternalTransferRequest>
        return envelope
    }

     private fun convertToLedgerEntriesRecordedEnvelope(evt: OutboxEvent): EventEnvelope<LedgerEntriesRecorded> {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, LedgerEntriesRecorded::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<LedgerEntriesRecorded>
        return envelope
    }
}
