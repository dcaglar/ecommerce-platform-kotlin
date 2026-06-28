package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.time.Utc
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
import java.util.UUID
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service

// Required for the cleaner Coroutines approach
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking

@Service
class OutboxRelayJob(
    private val centralOutboxRepository: CentralOutboxRelayPort,
    @Qualifier("batchPaymentEventPublisher") private val kafkaPublisher: EventPublisherPort,
    @Qualifier("resilientExecutor") private val executor: ThreadPoolTaskExecutor,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper,
    @Value("\${outbox-relay.batch-size:500}") private val batchSize: Int,
    @Value("\${app.instance-id:central-relay}") private val appInstanceId: String,
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
            val tSafe = centralOutboxRepository.computeTSafe() ?: Utc.nowInstant()

            // Generate a unique worker ID for this specific polling batch lock
            val workerId = "$appInstanceId-poll-${UUID.randomUUID()}"

            // 1. Use the V2 query that utilizes SKIP LOCKED and claimed_by
            val batch = centralOutboxRepository.findEligible(tSafe, batchSize, workerId)
            if (batch.isEmpty()) {
                return
            }

            logger.debug("Fetched {} eligible events (T_safe = {}, workerId = {})", batch.size, tSafe, workerId)

            val groups = batch.groupBy { it.aggregateId }

            for ((aggregateId, events) in groups) {
                // Submit the entire aggregate group to a single thread
                executor.execute {
                    // Bridge the Java Executor Thread to Kotlin Coroutines
                    runBlocking {
                        for (entry in events) {
                            try {
                                // 2. The .await() suspends this coroutine until Kafka ACKs, guaranteeing order.
                                processEntryAsync(entry).await()

                                // 3. The Success Path
                                logger.debug("✅ Marked dispatched outboxevent with ${entry.eventType} and oeid ${entry.oeid}")
                                centralOutboxRepository.markDispatched(
                                    entry.oeid,
                                    Utc.toInstant(entry.createdAt)
                                )
                                meterRegistry.counter("relay_published_total").increment()

                            } catch (exception: Exception) {
                                // 4. The Fail-Fast Path
                                logger.error("🛑 Breaking chain for aggregate $aggregateId at oeid=${entry.oeid}", exception)
                                meterRegistry.counter("relay_publish_failed_total").increment()

                                try {
                                    // Immediately release the lock so the next poll cycle can retry it
                                    centralOutboxRepository.unclaimSpecific(entry.oeid, Utc.toInstant(entry.createdAt),workerId)
                                } catch (dbEx: Exception) {
                                    logger.error("Failed to unclaim oeid=${entry.oeid}. Relies on reclaimer.", dbEx)
                                }

                                // Break the loop. Subsequent events for this aggregate are ignored to preserve order.
                                break
                            }
                        }
                    }
                }
            }
        } finally {
            sample.stop(meterRegistry.timer("relay_poll_duration"))
        }
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 120_000)
    fun reclaimStuck() {
        // 120 seconds is appropriate assuming Kafka's DELIVERY_TIMEOUT_MS is ~60-90s
        val reclaimed = centralOutboxRepository.reclaimStuck(staleSeconds = 120)
        if (reclaimed > 0) {
            logger.warn("⚠️ Reclaimed {} stuck PROCESSING events back to NEW", reclaimed)
            meterRegistry.counter("relay_reclaimed_total").increment(reclaimed.toDouble())
        }
    }

    private fun processEntryAsync(entry: OutboxEvent): CompletableFuture<*> {
        return try {
            logger.debug("🚀 OutboxRelayJob: Processing outbox event oeid={} of type={}", entry.oeid, entry.eventType)
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

    private fun <T : PaymentBaseEvent> convertToEnvelope(evt: OutboxEvent, clazz: Class<T>): EventEnvelope<T> {
        val envelopeType = objectMapper.typeFactory.constructParametricType(EventEnvelope::class.java, clazz)
        logger.debug("Outboxevent type is ${evt.eventType}, resolving EventEnvelope generic Event class to ${clazz.name}")
        return objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<T>
    }
}