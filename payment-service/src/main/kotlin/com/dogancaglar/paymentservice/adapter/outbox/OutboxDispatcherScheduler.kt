package com.dogancaglar.paymentservice.adapter.outbox

import com.dogancaglar.paymentservice.adapter.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.domain.port.OutboxEventRepository
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxDispatcherScheduler(
    private val outboxEventRepository: OutboxEventRepository,
    private val paymentEventPublisher: PaymentEventPublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun dispatchEvents() {
        val newEvents = outboxEventRepository.findByStatus("NEW")
        val updatedEvents = mutableListOf<OutboxEvent>()

        newEvents.forEach { event: OutboxEvent ->
            try {
                paymentEventPublisher.publish(
                    topic = event.eventType,
                    aggregateId = event.aggregateId,
                    eventType = event.eventType,
                    data = event.payload
                )
                event.markAsSent()
                updatedEvents.add(event)
            } catch (ex: Exception) {
                logger.error("Failed to dispatch outbox event [${event.aggregateId}]: ${ex.message}")
            }
        }

        if (updatedEvents.isNotEmpty()) {
            outboxEventRepository.saveAll(updatedEvents)
        }
    }
}