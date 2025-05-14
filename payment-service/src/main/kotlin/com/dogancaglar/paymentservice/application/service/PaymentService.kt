package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventRepository
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.PaymentRepository
import com.dogancaglar.paymentservice.domain.event.mapper.toCreatedEvent
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
        // Persist the payment entity
        paymentRepository.save(payment)

        val paymentOrderList = mutableListOf<PaymentOrder>()

        // Attempt PSP calls for each order
        for (order in payment.paymentOrders) {
            try {
                paymentOrderList.add(order);
            } catch (ex: Exception) {
                logger.warn("PSP call failed for PaymentOrder ${order.paymentOrderId}, falling back to async", ex)
            }

        }
        paymentOrderRepository.saveAll(paymentOrderList)
        // Save outbox events for failed orders
        val outboxEvents = buildOutboxEvents(paymentOrderList)
        if (outboxEvents.isNotEmpty()) {
            outboxEventRepository.saveAll(outboxEvents)
        }
        return payment
    }


    private fun buildOutboxEvents(paymentOrders: List<PaymentOrder>): List<OutboxEvent> {
        val outboxEvents = mutableListOf<OutboxEvent>()
        for (order in paymentOrders) {
            outboxEvents.add(createOutBoxEvent(order))
        }
        return outboxEvents
    }

    fun createOutBoxEvent( paymentOrder: PaymentOrder) : OutboxEvent{
        // first create PaymentOrderCreatedEvent then make it in an envelop wrap to generic it
        val event = paymentOrder.toCreatedEvent()
        val eventPayLoad = EventEnvelope.wrap(eventType = "payment_order_created", aggregateId = event.paymentOrderId, data = event)
        val json = objectMapper.writeValueAsString(eventPayLoad);
        return OutboxEvent(eventType = "payment_order_created", createdAt = LocalDateTime.now(), status = "NEW", aggregateId = event.paymentOrderId, payload = json)
    }
}
