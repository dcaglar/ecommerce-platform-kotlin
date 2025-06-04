package com.dogancaglar.paymentservice.adapter.outbox.producer

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxDispatcherScheduler(
    private val outboxEventPort: OutboxEventPort,
    private val paymentEventPublisher: PaymentEventPublisher,
    @Qualifier("myObjectMapper")
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun dispatchEvents() {
        val newEvents = outboxEventPort.findByStatus(
            "NEW"
        )

        logger.debug("Starting outbox dispatch cycle, found ${newEvents.size} new events")
        val updatedEvents = mutableListOf<OutboxEvent>()

        newEvents.forEach { outboxEvent: OutboxEvent ->
            val envelopeType = objectMapper
                .typeFactory
                .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)


            val envelope: EventEnvelope<PaymentOrderCreated> = objectMapper.readValue(outboxEvent.payload, envelopeType)
            LogContext.with(envelope) {
                try {
                    paymentEventPublisher.publish(
                        preSetEventIdFromCaller = envelope.eventId,
                        aggregateId = envelope.aggregateId,
                        eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                        data = envelope.data,
                        traceId = envelope.traceId
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
    }
}