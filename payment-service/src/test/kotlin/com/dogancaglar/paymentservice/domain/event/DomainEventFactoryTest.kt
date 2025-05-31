package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import org.assertj.core.api.Assertions.assertThat
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
        val eventType = "payment_order_created"
        LogContext.withTrace(traceIdFromMDC, "DomainEventFactoryTest") {
            // when
            val envelope: EventEnvelope<PaymentOrderCreated> = DomainEventEnvelopeFactory.envelopeFor(
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
                eventType = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = "paymentorder-1001"
            )

            // then
            assertThat(envelope.eventId).isNotNull
            assertThat (envelope.traceId).isEqualTo(traceIdFromMDC)
            assertThat(envelope.eventType).isEqualTo(eventType)
        }
    }

    @Test
    fun `should generate new traceId when not present in MDC`() {
        // when
        assertThrows <IllegalStateException> {
            DomainEventEnvelopeFactory.envelopeFor(
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
                eventType = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = "paymentorder-1"
            )

        }
    }

    @Test
    fun `should generate unique eventId and same traceid each time`() {
        val traceIdFromMDC = "test-traceid"
        LogContext.withTrace(traceIdFromMDC, "DomainEventFactoryTest") {
            val event1 = DomainEventEnvelopeFactory.envelopeFor(
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
                eventType = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = "paymentorder-1"
            )
            val event2 = DomainEventEnvelopeFactory.envelopeFor(
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
                eventType = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = "paymentorder-1"
            )
            assertThat(event1.traceId).isEqualTo(event2.traceId)
            assertThat(event1.eventId).isNotEqualTo(event2.eventId)

        }
    }

    @Test
    fun `envelopeFor should create EventEnvelope with correct fields`() {

        val traceIdFromMDC = "test-traceid"
        LogContext.withTrace(traceIdFromMDC, "DomainEventFactoryTest") {
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
                data = event,
                eventType = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = event.publicPaymentOrderId
            )

            assertThat(envelope.traceId).isNotBlank
            assertThat(envelope.eventId).isNotNull
            assertThat(envelope.eventType).isEqualTo(EventMetadatas.PaymentOrderCreatedMetadata.eventType)
            assertThat(envelope.aggregateId).isEqualTo(event.publicPaymentOrderId)
            assertThat(envelope.data).isEqualTo(event)
        }
    }

    @Test
    fun `should set parentEventId when provided`() {
        val traceIdFromMDC = "test-traceid"
        LogContext.withTrace(traceIdFromMDC, "DomainEventFactoryTest") {
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
                data = event,
                eventType = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = event.publicPaymentOrderId,
                parentEventId = parentId
            )
            assertThat(envelope.parentEventId).isEqualTo(parentId)
            assertThat(envelope.traceId).isEqualTo(traceIdFromMDC)
        }
    }

    @Test
    fun `should use traceId from MDC when traceId param is null`() {
        val mdcTraceId = "mdc-trace-123"
        try {
            MDC.put(LogFields.TRACE_ID, mdcTraceId)
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
                data = event,
                eventType = EventMetadatas.PaymentOrderCreatedMetadata,
                aggregateId = event.publicPaymentOrderId,
            )

            assertThat(envelope.traceId).isEqualTo(mdcTraceId)
        } finally {
            MDC.clear()
        }
    }

}