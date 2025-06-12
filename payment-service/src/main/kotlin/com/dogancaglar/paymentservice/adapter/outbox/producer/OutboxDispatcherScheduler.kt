package com.dogancaglar.paymentservice.adapter.outbox.producer

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.config.metrics.*
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
        private const val EVENT_TYPE = MetricTagValues.EventTypes.PAYMENT_ORDER_CREATED
    }

    // Used for the batch size gauge (we control the value)
    private val batchSize = AtomicReference(0.0)
    private val scheduledDelaySeconds = AtomicReference(0.0)
    private var lastExpectedRun = System.currentTimeMillis() + INTERVAL_MS

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        // (1) Gauge: Outbox backlog (computed on-the-fly)
        Gauge.builder(MetricNames.OUTBOX_EVENT_BACKLOG) { outboxEventPort.countByStatus("NEW") }
            .description("Number of outbox events still in NEW status")
            .tags(
                *tagsOf(
                    tag(MetricTags.FLOW, MetricTagValues.Flows.OUTBOX),
                    tag(MetricTags.EVENT_TYPE, EVENT_TYPE)
                )
            )
            .register(meterRegistry)

        // (2) Gauge: Current batch size (self-updated, see below)
        Gauge.builder(MetricNames.OUTBOX_DISPATCH_BATCH_SIZE, batchSize, AtomicReference<Double>::get)
            .description("Number of events processed in current batch")
            .tags(
                *tagsOf(
                    tag(MetricTags.FLOW, MetricTagValues.Flows.OUTBOX),
                    tag(MetricTags.EVENT_TYPE, EVENT_TYPE)
                )
            )
            .register(meterRegistry)

        // (3) Gauge: Scheduled delay (self-updated)
        Gauge.builder(MetricNames.OUTBOX_DISPATCH_DELAY_SECONDS, scheduledDelaySeconds, AtomicReference<Double>::get)
            .description("Delay between expected and actual job start (s)")
            .tags(
                *tagsOf(
                    tag(MetricTags.FLOW, MetricTagValues.Flows.OUTBOX),
                    tag(MetricTags.JOB_NAME, MetricTagValues.Jobs.OUTBOX_DISPATCHER)
                )
            )
            .register(meterRegistry)
    }

    @Scheduled(fixedDelayString = "\${outbox.dispatch.delay:5000}")
    @Transactional
    fun dispatchEvents() {
        // Update delay gauge
        val now = System.currentTimeMillis()
        val delayMs = now - lastExpectedRun
        scheduledDelaySeconds.set(delayMs.coerceAtLeast(0L) / 1_000.0)
        lastExpectedRun += INTERVAL_MS

        // Start timing the job
        meterRegistry.timer(
            MetricNames.OUTBOX_JOB_DURATION_SECONDS,
            *tagsOf(
                tag(MetricTags.FLOW, MetricTagValues.Flows.OUTBOX),
                tag(MetricTags.JOB_NAME, MetricTagValues.Jobs.OUTBOX_DISPATCHER)
            )
        ).record(Runnable {
            val newEvents = outboxEventPort.findByStatus("NEW")
            batchSize.set(newEvents.size.toDouble()) // Update batch size gauge

            val processedCounter = meterRegistry.counter(
                MetricNames.OUTBOX_DISPATCHED_TOTAL,
                *tagsOf(
                    tag(MetricTags.FLOW, MetricTagValues.Flows.OUTBOX),
                    tag(MetricTags.EVENT_TYPE, EVENT_TYPE)
                )
            )
            val failedCounter = meterRegistry.counter(
                MetricNames.OUTBOX_DISPATCH_FAILED_TOTAL,
                *tagsOf(
                    tag(MetricTags.FLOW, MetricTagValues.Flows.OUTBOX),
                    tag(MetricTags.EVENT_TYPE, EVENT_TYPE)
                )
            )
            val publishFailedCounter = meterRegistry.counter(
                MetricNames.OUTBOX_PUBLISH_FAILED_TOTAL,
                *tagsOf(
                    tag(MetricTags.FLOW, MetricTagValues.Flows.OUTBOX),
                    tag(MetricTags.EVENT_TYPE, EVENT_TYPE)
                )
            )

            val updated = mutableListOf<OutboxEvent>()

            newEvents.forEach { outboxEvent ->
                try {
                    val envelopeType = objectMapper.typeFactory
                        .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)
                    val envelope: EventEnvelope<PaymentOrderCreated> =
                        objectMapper.readValue(outboxEvent.payload, envelopeType)

                    LogContext.with(envelope) {
                        // Record event's outbox lag
                        val eventDelay = Duration.between(outboxEvent.createdAt, LocalDateTime.now())
                        meterRegistry.timer(
                            MetricNames.OUTBOX_DISPATCH_DELAY_SECONDS,
                            *tagsOf(
                                tag(MetricTags.FLOW, MetricTagValues.Flows.OUTBOX),
                                tag(MetricTags.EVENT_TYPE, EVENT_TYPE)
                            )
                        ).record(eventDelay)

                        // Try to publish to Kafka, record publish failures separately
                        try {
                            paymentEventPublisher.publish(
                                preSetEventIdFromCaller = envelope.eventId,
                                aggregateId = envelope.aggregateId,
                                eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                                data = envelope.data,
                                traceId = envelope.traceId,
                                parentEventId = envelope.eventId
                            )
                        } catch (pubEx: Exception) {
                            publishFailedCounter.increment()
                            logger.error("Failed to publish to Kafka: ${pubEx.message}")
                        }

                        processedCounter.increment()
                        outboxEvent.markAsSent()
                        updated.add(outboxEvent)
                    }
                } catch (ex: Exception) {
                    failedCounter.increment()
                    logger.error("Failed to dispatch outbox event [${outboxEvent.aggregateId}]: ${ex.message}")
                }
            }

            // Record DB write time (only if needed)
            if (updated.isNotEmpty()) {
                meterRegistry.timer(
                    MetricNames.OUTBOX_DB_WRITE_SECONDS,
                    *tagsOf(
                        tag(MetricTags.FLOW, MetricTagValues.Flows.OUTBOX),
                        tag(MetricTags.EVENT_TYPE, EVENT_TYPE)
                    )
                ).record(Runnable {
                    outboxEventPort.saveAll(updated)
                })
            }
        })
    }
}