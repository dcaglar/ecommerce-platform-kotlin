package com.dogancaglar.paymentservice.adapter.outbox.producer

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
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

@Component
class OutboxDispatcherScheduler(
    private val outboxEventPort: OutboxEventPort,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val meterRegistry: MeterRegistry,
    @Qualifier("myObjectMapper")
    private val objectMapper: ObjectMapper
) {
    private val intervalMs = 5_000L
    private var lastExpectedRun = System.currentTimeMillis()
    private val logger = LoggerFactory.getLogger(javaClass)


    init {
        Gauge.builder("outbox_event_backlog") {
            // lean query to count rows in NEW status
            outboxEventPort.countByStatus("NEW")
        }
            .description("Number of outbox events still in NEW status")
            .register(meterRegistry)
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun dispatchEvents() {
        val startTime = System.nanoTime()
        meterRegistry.timer("outbox_dispatch_execution_seconds").record(Runnable {
            val newEvents = outboxEventPort.findByStatus(
                "NEW"
            )

            logger.debug("Starting outbox dispatch cycle, found ${newEvents.size} new events")
            val updatedEvents = mutableListOf<OutboxEvent>()

            newEvents.forEach { outboxEvent: OutboxEvent ->
                val envelopeType = objectMapper
                    .typeFactory
                    .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)


                val envelope: EventEnvelope<PaymentOrderCreated> =
                    objectMapper.readValue(outboxEvent.payload, envelopeType)
                LogContext.with(envelope) {
                    try {
                        val delay = Duration.between(outboxEvent.createdAt, LocalDateTime.now())
                        meterRegistry.timer("outbox.dispatch.delay.ms", "eventType", outboxEvent.eventType)
                            .record(delay)
                        logger.info("Dispatching outbox event [${outboxEvent.aggregateId}] after delay of ${delay.toMillis()} ms")
                        paymentEventPublisher.publish(
                            preSetEventIdFromCaller = envelope.eventId,
                            aggregateId = envelope.aggregateId,
                            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                            data = envelope.data,
                            traceId = envelope.traceId,
                            parentEventId = envelope.eventId
                        )
                        outboxEvent.markAsSent()
                        updatedEvents.add(outboxEvent)
                    } catch (ex: Exception) {
                        logger.error("Failed to dispatch outbox event [${outboxEvent.aggregateId}]: ${ex.message}")
                    } finally {
                        logger.info("an outbox event persisted succesully")
                    }
                }
            }

            if (updatedEvents.isNotEmpty()) {
                outboxEventPort.saveAll(updatedEvents)
            }
        })
        val durationNanos = Duration.ofNanos(System.nanoTime() - startTime)
        meterRegistry.timer("outbox_dispatcher_latency_seconds")
            .record(durationNanos)
    }
}