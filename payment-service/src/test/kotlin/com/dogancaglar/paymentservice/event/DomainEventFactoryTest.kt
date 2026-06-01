package com.dogancaglar.paymentservice.event

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.GenericLogFields
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventEntityMapper
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.MDC
import java.util.UUID

class DomainEventFactoryTest {

    @Test
    fun `should create EventEnvelope with generated eventId and traceid from mdc`() {
        // given
        val traceIdFromMDC = "test-traceid"
        MDC.put(GenericLogFields.TRACE_ID, traceIdFromMDC)
        try {
            val now = Utc.nowLocalDateTime()
            val paymentOrder = PaymentOrder.rehydrate(
                paymentOrderId = PaymentOrderId(1001L),
                paymentId = PaymentId(123L),
                sellerId = SellerId("seller-1"),
                amount = Amount.of(25000L, Currency("EUR")),
                status = PaymentOrderStatus.CAPTURE_RECEIVED,
                retryCount = 0,
                createdAt = now,
                updatedAt = now
            )
            val event = PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureReceived(paymentOrder)
            
            // when
            val envelope: EventEnvelope<PaymentOrderCaptureReceived> = EventEnvelopeFactory.envelopeFor(
                data = event,
                aggregateId = event.paymentOrderId,
                traceId = traceIdFromMDC
            )

            // then
            Assertions.assertThat(envelope.eventId).isNotNull
            Assertions.assertThat(envelope.traceId).isEqualTo(traceIdFromMDC)
            Assertions.assertThat(envelope.eventType).isEqualTo("payment_order_capture_received")
        } finally {
            MDC.clear()
        }
    }

    @Test
    fun `should generate new traceId when not present in MDC`() {
        // when
        assertThrows<IllegalStateException> {
            val now = Utc.nowLocalDateTime()
            val paymentOrder = PaymentOrder.rehydrate(
                paymentOrderId = PaymentOrderId(1L),
                paymentId = PaymentId(1L),
                sellerId = SellerId("s-1"),
                amount = Amount.of(1000L, Currency("EUR")),
                status = PaymentOrderStatus.CAPTURE_RECEIVED,
                retryCount = 0,
                createdAt = now,
                updatedAt = now
            )
            val event = PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureReceived(paymentOrder)
            
            EventEnvelopeFactory.envelopeFor(
                data = event,
                aggregateId = event.paymentOrderId,
                traceId = MDC.get(GenericLogFields.TRACE_ID) ?: throw IllegalStateException("TraceId missing")
            )
        }
    }

    @Test
    fun `should generate unique eventId and same traceid each time`() {
        val traceIdFromMDC = "test-traceid"
        MDC.put(GenericLogFields.TRACE_ID, traceIdFromMDC)
        try {
            val now = Utc.nowLocalDateTime()
            val paymentOrder1 = PaymentOrder.rehydrate(
                paymentOrderId = PaymentOrderId(1L),
                paymentId = PaymentId(2L),
                sellerId = SellerId("s-1"),
                amount = Amount.of(1000L, Currency("EUR")),
                status = PaymentOrderStatus.CAPTURE_RECEIVED,
                retryCount = 0,
                createdAt = now,
                updatedAt = now
            )
            val event1Data = PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureReceived(paymentOrder1)
            val event1 = EventEnvelopeFactory.envelopeFor(
                data = event1Data,
                aggregateId = event1Data.paymentOrderId,
                traceId = traceIdFromMDC
            )
            
            val paymentOrder2 = PaymentOrder.rehydrate(
                paymentOrderId = PaymentOrderId(3L),
                paymentId = PaymentId(2L),
                sellerId = SellerId("s-1"),
                amount = Amount.of(1000L, Currency("EUR")),
                status = PaymentOrderStatus.CAPTURE_RECEIVED,
                retryCount = 0,
                createdAt = now,
                updatedAt = now
            )
            val event2Data = PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureReceived(paymentOrder2)
            val event2 = EventEnvelopeFactory.envelopeFor(
                data = event2Data,
                aggregateId = event2Data.paymentOrderId,
                traceId = traceIdFromMDC
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
            val now = Utc.nowLocalDateTime()
            val paymentOrder = PaymentOrder.rehydrate(
                paymentOrderId = PaymentOrderId(1001L),
                paymentId = PaymentId(123L),
                sellerId = SellerId("seller-1"),
                amount = Amount.of(25000L, Currency("EUR")),
                status = PaymentOrderStatus.CAPTURE_RECEIVED,
                retryCount = 0,
                createdAt = now,
                updatedAt = now
            )
            val event = PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureReceived(paymentOrder)

            val envelope = EventEnvelopeFactory.envelopeFor(
                data = event,
                aggregateId = event.publicPaymentOrderId,
                traceId = traceIdFromMDC
            )

            Assertions.assertThat(envelope.traceId).isNotBlank
            Assertions.assertThat(envelope.eventId).isNotNull
            Assertions.assertThat(envelope.eventType).isEqualTo("payment_order_capture_received")
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
            val parentId = UUID.randomUUID().toString()
            val now = Utc.nowLocalDateTime()
            val paymentOrder = PaymentOrder.rehydrate(
                paymentOrderId = PaymentOrderId(1001L),
                paymentId = PaymentId(123L),
                sellerId = SellerId("seller-1"),
                amount = Amount.of(25000L, Currency("EUR")),
                status = PaymentOrderStatus.CAPTURE_RECEIVED,
                retryCount = 0,
                createdAt = now,
                updatedAt = now
            )
            val event = PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureReceived(paymentOrder)

            val envelope = EventEnvelopeFactory.envelopeFor(
                data = event,
                aggregateId = event.publicPaymentOrderId,
                traceId = traceIdFromMDC,
                parentEventId = parentId
            )
            Assertions.assertThat(envelope.parentEventId).isEqualTo(parentId)
            Assertions.assertThat(envelope.traceId).isEqualTo(traceIdFromMDC)
        } finally {
            MDC.clear()
        }
    }

}