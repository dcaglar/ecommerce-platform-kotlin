package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventRepository
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import com.dogancaglar.paymentservice.domain.port.PaymentRepository
import com.dogancaglar.paymentservice.domain.event.mapper.toCreatedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val outboxEventRepository: OutboxEventRepository,
    @Qualifier("myObjectMapper")
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    @Transactional
    fun createPayment(payment: Payment): Payment {
        // Persist the payment entity
        paymentRepository.save(payment)

        val paymentOrderList = mutableListOf<PaymentOrder>()

        // each order is represent on payment request to PSP
        for (order in payment.paymentOrders) {
            try {
                paymentOrderList.add(order);
            } catch (ex: Exception) {
                logger.warn("PSP call failed for PaymentOrder ${order.paymentOrderId}, falling back to async", ex)
            }

        }
        paymentOrderRepository.saveAll(paymentOrderList)
        val outboxEvents = buildOutboxEvents(paymentOrderList)
        if (outboxEvents.isNotEmpty()) {
            outboxEventRepository.saveAll(outboxEvents)
        }
        return payment
    }


    private fun buildOutboxEvents(paymentOrders: List<PaymentOrder>): List<OutboxEvent> {
        val outboxEvents = mutableListOf<OutboxEvent>()
        for (order in paymentOrders) {
            outboxEvents.add(toOutBoxEvent(order))
        }
        return outboxEvents
    }

    fun toOutBoxEvent(paymentOrder: PaymentOrder) : OutboxEvent{
        // first create PaymentOrderCreatedEvent then make it in an envelop wrap to generic it
        val event = paymentOrder.toCreatedEvent()
        /*
         eventType: String,
        aggregateId: String,
        data: T,
        traceId: String? = null,
        parentEventId: UUID? = null
         */
        logger.info("Generate outbox event paymentOrderId ${paymentOrder.paymentOrderId} and traceid=${MDC.get(LogFields.TRACE_ID)}")
        val eventPayLoad = EventEnvelope.wrap(eventType = "payment_order_created",
            aggregateId = paymentOrder.paymentOrderId,
            data = event,
            traceId = MDC.get(
            LogFields.TRACE_ID)?: UUID.randomUUID().toString())
        val json = objectMapper.writeValueAsString(eventPayLoad);
        return OutboxEvent(eventId = eventPayLoad.eventId, eventType = "payment_order_created", createdAt = LocalDateTime.now(), status = "NEW", aggregateId = eventPayLoad.aggregateId, payload = json)
    }

}
