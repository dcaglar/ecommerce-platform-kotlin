package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.common.event.DomainEventFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class DomainEventFactoryTest {

    @Test
    fun `should create EventEnvelope with generated eventId and given traceId`() {
        // given
        val traceId = UUID.randomUUID().toString()
        val eventType = "payment_order_created"
        val paymentOrderEvent = PaymentOrderCreated(
            paymentOrderId = "po-1001",
            publicPaymentOrderId = "paymentorder-1001",
            paymentId = "p-123",
            publicPaymentId = "payment-123",
            sellerId = "seller-1",
            amountValue = BigDecimal("250.00"),
            currency = "EUR",
            status = "CREATED",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            retryCount = 0
        )

        // when
        val envelope: EventEnvelope<PaymentOrderCreated> = DomainEventFactory.envelopeFor(
            event = paymentOrderEvent,
            eventType = eventType,
            aggregateId = paymentOrderEvent.publicPaymentOrderId,
            traceId = traceId,
            parentEventId = null
        )

        // then
        assertThat(envelope).isNotNull
        assertThat(envelope.traceId).isEqualTo(traceId)
        assertThat(envelope.aggregateId).isEqualTo("paymentorder-1001")
        assertThat(envelope.eventType).isEqualTo(eventType)
        assertThat(envelope.parentEventId).isNull()
        assertThat(envelope.eventId).isNotNull
        assertThat(envelope.data).isEqualTo(paymentOrderEvent)
    }

    @Test
    fun `should generate new traceId when not present in MDC`() {
        // when
        val envelope = DomainEventFactory.envelopeFor(
            event = "dummy",
            eventType = "dummy_event",
            aggregateId = "agg-1"
        )

        // then
        assertThat(envelope.traceId).isNotBlank()
        assertThat(UUID.fromString(envelope.traceId)).isInstanceOf(UUID::class.java)
    }

    @Test
    fun `should generate unique eventId each time`() {
        val event1 = DomainEventFactory.envelopeFor("e1", "type", "agg")
        val event2 = DomainEventFactory.envelopeFor("e2", "type", "agg")

        assertThat(event1.eventId).isNotEqualTo(event2.eventId)
    }

}