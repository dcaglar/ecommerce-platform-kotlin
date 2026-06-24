package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.CaptureRequested
import com.dogancaglar.paymentservice.application.events.CaptureSubmitted
import com.dogancaglar.paymentservice.application.events.CaptureConfirmed
import com.dogancaglar.paymentservice.application.events.InternalTransferCommand
import com.dogancaglar.paymentservice.application.events.JournalEntriesRecorded
import com.dogancaglar.paymentservice.application.events.OutboxEventTypes
import com.dogancaglar.paymentservice.application.events.PaymentBaseEvent
import com.dogancaglar.paymentservice.application.events.SettlementReceived

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

    @Scheduled(
        initialDelayString = "\${outbox-relay.initial-delay:15000}",
        fixedDelayString = "\${outbox-relay.poll-interval:5000}"
    )
    fun poll() {
        val sample = Timer.start(meterRegistry)
        try {
            val tSafe = centralOutboxRepository.computeTSafe() ?: com.dogancaglar.common.time.Utc.nowInstant()

            val batch = centralOutboxRepository.findEligible(tSafe, batchSize)
            if (batch.isEmpty()) {
                return
            }
            
            logger.debug("Fetched {} eligible events (T_safe = {})", batch.size, tSafe)

            val groups = batch.groupBy { it.aggregateId }

            for ((_, events) in groups) {
                executor.execute {
                    for (entry in events) {
                        val future = processEntryAsync(entry)
                        
                        future.thenAccept {
                            logger.info("✅ Marked dispatched outboxevent with ${entry.eventType} and oeid ${entry.oeid}  and status ${entry.status}")
                            centralOutboxRepository.markDispatched(entry.oeid, com.dogancaglar.common.time.Utc.toInstant(entry.createdAt))
                            meterRegistry.counter("relay_published_total").increment()
                        }.exceptionally { exception ->
                            meterRegistry.counter("relay_publish_failed_total").increment()
                            logger.error("Failed to publish event oeid=${entry.oeid} for aggregate ${entry.aggregateId}", exception)
                            null
                        }

                        // If the future failed synchronously, break the loop to preserve ordering
                        if (future.isCompletedExceptionally) {
                            logger.error("🛑 Breaking group processing for aggregate ${entry.aggregateId} due to synchronous failure")
                            break
                        }
                    }
                }
            }
        } finally {
            sample.stop(meterRegistry.timer("relay_poll_duration"))
        }
    }
    /*

     */

    private fun processEntryAsync(entry: OutboxEvent): CompletableFuture<*> {
        return try {
            logger.info("🚀 OutboxRelayJob: Processing outbox event oeid={} of type={}", entry.oeid, entry.eventType)
            when (OutboxEventTypes.from(entry.eventType)) {
                OutboxEventTypes.PAYMENT_AUTHORIZED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, PaymentAuthorized::class.java))
                OutboxEventTypes.CAPTURE_REQUESTED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, CaptureRequested::class.java))
                OutboxEventTypes.CAPTURE_SUBMITTED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, CaptureSubmitted::class.java))
                OutboxEventTypes.CAPTURE_CONFIRMED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, CaptureConfirmed::class.java))
                OutboxEventTypes.JOURNAL_ENTRIES_RECORDED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, JournalEntriesRecorded::class.java))
                OutboxEventTypes.INTERNAL_TRANSFER_COMMAND -> kafkaPublisher.publishAsync(convertToEnvelope(entry, InternalTransferCommand::class.java))
                OutboxEventTypes.SETTLEMENT_RECEIVED -> kafkaPublisher.publishAsync(convertToEnvelope(entry, SettlementReceived::class.java))
                else -> {
                    logger.warn("❓ Unknown outbox event type=${entry.eventType}, skipping oeid=${entry.oeid}")
                    CompletableFuture.completedFuture<Any>(null)
                }
            }
        } catch (e: Exception) {
            CompletableFuture.failedFuture<Any>(e)
        }
    }


    // Replace all 6 custom envelope conversion methods with this single robust implementation:
    private fun <T : PaymentBaseEvent> convertToEnvelope(evt: OutboxEvent, clazz: Class<T>): EventEnvelope<T> {
        val envelopeType = objectMapper.typeFactory.constructParametricType(EventEnvelope::class.java, clazz)
        logger.info("Outboxevent. type is ${evt.eventType} , but EventEnvelopetype Generic Event class is ${clazz.name}" )
        return objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<T>
    }
}
