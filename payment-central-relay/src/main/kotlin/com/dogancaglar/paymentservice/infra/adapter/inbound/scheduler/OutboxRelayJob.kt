package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.CaptureRequested
import com.dogancaglar.paymentservice.application.events.CaptureSubmitted
import com.dogancaglar.paymentservice.application.events.CaptureConfirmed
import com.dogancaglar.paymentservice.application.events.InternalTransferRequested
import com.dogancaglar.paymentservice.application.events.JournalEntriesRecorded
import com.dogancaglar.paymentservice.application.events.OutboxEventTypes
import com.dogancaglar.paymentservice.application.events.PaymentBaseEvent

import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxRelayPort
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
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
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper,
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
    /*

     */

    private fun processEntryAsync(entry: OutboxEvent): CompletableFuture<Void> {
        val future = try {
            when (OutboxEventTypes.from(entry.eventType)) {
                OutboxEventTypes.PAYMENT_AUTHORIZED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, PaymentAuthorized::class.java))
                OutboxEventTypes.CAPTURE_REQUESTED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, CaptureRequested::class.java))
                OutboxEventTypes.CAPTURE_SUBMITTED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, CaptureSubmitted::class.java))
                OutboxEventTypes.CAPTURE_CONFIRMED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, CaptureConfirmed::class.java))
                OutboxEventTypes.INTERNAL_TRANSFER_REQUESTED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, InternalTransferRequested::class.java))
                OutboxEventTypes.LEDGER_ENTRIES_RECORDED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, JournalEntriesRecorded::class.java))
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


    // Replace all 6 custom envelope conversion methods with this single robust implementation:
    private fun <T : PaymentBaseEvent> convertToEnvelope(evt: OutboxEvent, clazz: Class<T>): EventEnvelope<T> {
        val envelopeType = objectMapper.typeFactory.constructParametricType(EventEnvelope::class.java, clazz)
        return objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<T>
    }
}
