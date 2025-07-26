package com.dogancaglar.paymentservice.event

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.GenericLogFields
import com.dogancaglar.paymentservice.domain.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.MDC
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class DomainEventFactoryTest {

    @Test
    fun `should create EventEnvelope with generated eventId and traceid from mdc`() {
        // given
        val traceIdFromMDC = "test-traceid"
        MDC.put(GenericLogFields.TRACE_ID, traceIdFromMDC)
        try {
            // when
            val envelope: EventEnvelope<PaymentOrderCreated> = DomainEventEnvelopeFactory.envelopeFor(
                traceId = traceIdFromMDC,
                data = PaymentOrderCreated(
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
                ),
                eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = "paymentorder-1001"
            )

            // then
            Assertions.assertThat(envelope.eventId).isNotNull
            Assertions.assertThat(envelope.traceId).isEqualTo(traceIdFromMDC)
            Assertions.assertThat(envelope.eventType).isEqualTo("payment_order_created")
        } finally {
            MDC.clear()
        }
    }

    @Test
    fun `should generate new traceId when not present in MDC`() {
        // when
        assertThrows<IllegalStateException> {
            DomainEventEnvelopeFactory.envelopeFor(
                traceId = MDC.get(GenericLogFields.TRACE_ID) ?: throw IllegalStateException("TraceId missing"),
                data = PaymentOrderCreated(
                    paymentOrderId = "po-1",
                    publicPaymentOrderId = "paymentorder-1",
                    paymentId = "p-1",
                    publicPaymentId = "payment-1",
                    sellerId = "s-1",
                    amountValue = BigDecimal.TEN,
                    currency = "EUR",
                    status = "CREATED",
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                    retryCount = 0
                ),
                eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = "paymentorder-1"
            )

        }
    }

    @Test
    fun `should generate unique eventId and same traceid each time`() {
        val traceIdFromMDC = "test-traceid"
        MDC.put(GenericLogFields.TRACE_ID, traceIdFromMDC)
        try {
            val event1 = DomainEventEnvelopeFactory.envelopeFor(
                traceId = traceIdFromMDC,
                data = PaymentOrderCreated(
                    paymentOrderId = "po-1",
                    publicPaymentOrderId = "paymentorder-1",
                    paymentId = "p-1",
                    publicPaymentId = "payment-1",
                    sellerId = "s-1",
                    amountValue = BigDecimal.TEN,
                    currency = "EUR",
                    status = "CREATED",
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                    retryCount = 0
                ),
                eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = "paymentorder-1"
            )
            val event2 = DomainEventEnvelopeFactory.envelopeFor(
                traceId = traceIdFromMDC,
                data = PaymentOrderCreated(
                    paymentOrderId = "po-3",
                    publicPaymentOrderId = "paymentorder-1",
                    paymentId = "p-2",
                    publicPaymentId = "payment-1",
                    sellerId = "s-1",
                    amountValue = BigDecimal.TEN,
                    currency = "EUR",
                    status = "CREATED",
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now(),
                    retryCount = 0
                ),
                eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = "paymentorder-1"
            )
            Assertions.assertThat(event1.traceId).isEqualTo(event2.traceId)
            Assertions.assertThat(event1.eventId).isNotEqualTo(event2.eventId)

        } finally {
            MDC.clear()
        }
    }

    @Test
    fun `envelopeFor should create EventEnvelope with correct fields`() {

        val traceIdFromMDC = "test-traceid"
        MDC.put(GenericLogFields.TRACE_ID, traceIdFromMDC)
        try {
            val event = PaymentOrderCreated(
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

            val envelope = DomainEventEnvelopeFactory.envelopeFor(
                traceId = traceIdFromMDC,
                data = event,
                eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = event.publicPaymentOrderId
            )

            Assertions.assertThat(envelope.traceId).isNotBlank
            Assertions.assertThat(envelope.eventId).isNotNull
            Assertions.assertThat(envelope.eventType).isEqualTo(EventMetadatas.PaymentOrderCreatedMetadata.eventType)
            Assertions.assertThat(envelope.aggregateId).isEqualTo(event.publicPaymentOrderId)
            Assertions.assertThat(envelope.data).isEqualTo(event)
        } finally {
            MDC.clear()
        }
    }

    @Test
    fun `should set parentEventId when provided`() {
        val traceIdFromMDC = "test-traceid"
        MDC.put(GenericLogFields.TRACE_ID, traceIdFromMDC)
        try {
            val parentId = UUID.randomUUID()
            val event = PaymentOrderCreated(
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

            val envelope = DomainEventEnvelopeFactory.envelopeFor(
                traceId = traceIdFromMDC,
                data = event,
                eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = event.publicPaymentOrderId,
                parentEventId = parentId
            )
            Assertions.assertThat(envelope.parentEventId).isEqualTo(parentId)
            Assertions.assertThat(envelope.traceId).isEqualTo(traceIdFromMDC)
        } finally {
            MDC.clear()
        }
    }

}