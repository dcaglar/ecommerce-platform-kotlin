package com.dogancaglar.paymentservice.adapter.outbox.producer

import ch.qos.logback.classic.LoggerContext
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.collections.forEach

@Component
class OutboxDispatcherScheduler(
    private val outboxEventRepository: OutboxEventRepository,
    private val paymentEventPublisher: PaymentEventPublisher,
    @Qualifier("myObjectMapper")
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun dispatchEvents() {
        val newEvents = outboxEventRepository.findByStatus("NEW")
        val updatedEvents = mutableListOf<OutboxEvent>()

        newEvents.forEach { outboxEvent: OutboxEvent ->
            val envelopeType = objectMapper
                .typeFactory
                .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)


                val envelope: EventEnvelope<PaymentOrderCreated> = objectMapper.readValue(outboxEvent.payload,envelopeType)
                LogContext.with(envelope) {
                    try {
                        paymentEventPublisher.publish(
                            aggregateId = envelope.aggregateId,
                            event = EventMetadatas.PaymentOrderCreatedMetadata,
                            data = envelope.data,
                            parentEnvelope = envelope
                        )
                        outboxEvent.markAsSent()
                        updatedEvents.add(outboxEvent)
                    }catch (ex: Exception) {
                        logger.error("Failed to dispatch outbox event [${outboxEvent.aggregateId}]: ${ex.message}")
                    } finally {
                        logger.info("an outbox event persisted succesully")
                    }
                }
        }

        if (updatedEvents.isNotEmpty()) {
            outboxEventRepository.saveAll(updatedEvents)
        }
    }
}