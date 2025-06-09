package com.dogancaglar.paymentservice.adapter.outbox.producer

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.config.metrics.JobMetrics
import com.dogancaglar.paymentservice.config.metrics.MetricTags
import com.dogancaglar.paymentservice.config.metrics.OutboxMetrics
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference

@Component
class OutboxDispatcherScheduler(
    private val outboxEventPort: OutboxEventPort,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val meterRegistry: MeterRegistry,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) {
    companion object {
        private const val INTERVAL_MS = 5_000L
        private const val EVENT_TYPE = "payment_order_created"
    }

    private var lastExpectedRun = System.currentTimeMillis() + INTERVAL_MS
    private val scheduledDelaySeconds = AtomicReference(0.0)
    private val logger = LoggerFactory.getLogger(javaClass)

    // 1) Backlog gauge: number of NEW outbox events
    init {
        Gauge.builder(OutboxMetrics.backlog(EVENT_TYPE)) { outboxEventPort.countByStatus("NEW") }
            .description("Number of outbox events still in NEW status")
            .register(meterRegistry)

        // 2) Scheduled‐delay gauge: how late we are vs expected schedule
        Gauge.builder(JobMetrics.SCHEDULED_JOB_DELAY) { scheduledDelaySeconds.get() }
            .description("Delay between expected and actual job start (s)")
            .tag(MetricTags.JOB, MetricTags.Jobs.OUTBOX_DISPATCHER)
            .register(meterRegistry)
    }

    @Scheduled(fixedDelayString = "\${outbox.dispatch.delay:5000}")
    @Transactional
    fun dispatchEvents() {
        // A) Compute & record delay
        val now = System.currentTimeMillis()
        val delayMs = now - lastExpectedRun
        scheduledDelaySeconds.set(delayMs.coerceAtLeast(0L) / 1_000.0)
        lastExpectedRun += INTERVAL_MS

        // B) Time the actual work
        meterRegistry
            .timer(OutboxMetrics.jobDuration(EVENT_TYPE), MetricTags.JOB, MetricTags.Jobs.OUTBOX_DISPATCHER)
            .record(Runnable {
                val newEvents = outboxEventPort.findByStatus("NEW")
                logger.debug("Starting outbox dispatch, found ${newEvents.size} events")
                val updated = mutableListOf<OutboxEvent>()
                newEvents.forEach { outboxEvent ->
                    val envelopeType = objectMapper.typeFactory
                        .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)
                    val envelope: EventEnvelope<PaymentOrderCreated> =
                        objectMapper.readValue(outboxEvent.payload, envelopeType)

                    LogContext.with(envelope) {
                        try {
                            val eventDelay = Duration.between(outboxEvent.createdAt, LocalDateTime.now())
                            meterRegistry.timer(
                                OutboxMetrics.dispatchDelay(EVENT_TYPE), MetricTags.EVENT_TYPE,
                                MetricTags.EventTypes.PAYMENT_ORDER_CREATED
                            )
                                .record(eventDelay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)

                            paymentEventPublisher.publish(
                                preSetEventIdFromCaller = envelope.eventId,
                                aggregateId = envelope.aggregateId,
                                eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                                data = envelope.data,
                                traceId = envelope.traceId,
                                parentEventId = envelope.eventId
                            )
                            meterRegistry.counter(
                                OutboxMetrics.processed(EVENT_TYPE), MetricTags.EVENT_TYPE,
                                MetricTags.EventTypes.PAYMENT_ORDER_CREATED
                            )
                                .increment()
                            outboxEvent.markAsSent()
                            updated.add(outboxEvent)
                        } catch (ex: Exception) {
                            meterRegistry.counter(
                                OutboxMetrics.failed(EVENT_TYPE),
                                MetricTags.EVENT_TYPE,
                                MetricTags.EventTypes.PAYMENT_ORDER_CREATED
                            ).increment()
                            logger.error("Failed to dispatch outbox event [${outboxEvent.aggregateId}]: ${ex.message}")
                        } finally {
                            logger.info("Outbox event [${outboxEvent.aggregateId}] processed")
                        }
                    }
                }
                if (updated.isNotEmpty()) {
                    outboxEventPort.saveAll(updated)
                }
            })
    }
}