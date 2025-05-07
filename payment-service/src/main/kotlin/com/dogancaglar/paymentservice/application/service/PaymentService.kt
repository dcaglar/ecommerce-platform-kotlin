package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventRepository
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.PaymentRepository
import com.dogancaglar.paymentservice.domain.event.toCreatedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    @Transactional
    fun createPayment(payment: Payment): Payment {
        paymentRepository.save(payment)

        val (successfulOrders, failedOrders) = payment.paymentOrders.map { order ->
            try {
                if (callPsp(order)) order.markAsPaid() to null
                else null to order
            } catch (ex: Exception) {
                logger.warn("PSP call failed for PaymentOrder ${order.paymentOrderId}, falling back to async", ex)
                null to order
            }
        }.unzip()

        val persistedOrders = (successfulOrders.filterNotNull() + failedOrders.filterNotNull())
        paymentOrderRepository.saveAll(persistedOrders)

        val outboxEvents = buildOutboxEvents(failedOrders.filterNotNull())
        if (outboxEvents.isNotEmpty()) {
            outboxEventRepository.saveAll(outboxEvents)
        }

        logger.info("Processed payment ${payment.id}: ${successfulOrders.count { it != null }} succeeded, ${failedOrders.count { it != null }} will be retried")

        return payment
    }

    private fun buildOutboxEvents(failedOrders: List<PaymentOrder>): List<OutboxEvent> {
        return failedOrders.map {
            val eventPayload = EventEnvelope.wrap(
                eventType = "payment_order_created",
                aggregateId = it.paymentOrderId,
                data = it.toCreatedEvent()
            )
            val payload = objectMapper.writeValueAsString(eventPayload)

            OutboxEvent(
                id = null,
                eventType = eventPayload.eventType,
                aggregateId = eventPayload.aggregateId,
                payload = payload,
                status = "NEW",
                createdAt = LocalDateTime.now()
            )
        }
    }

    // This should be replaced with your actual PSP integration
    private fun callPsp(order: PaymentOrder): Boolean {
        return UUID.randomUUID().leastSignificantBits % 2 == 0L
    }
}
