package com.dogancaglar.paymentservice.event

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.GenericLogFields
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.CaptureReceived
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
            val now = Utc.nowInstant()
            val event = CaptureReceived.from(
                captureTxId = 1L,
                paymentIntentId = "1001",
                publicPaymentIntentId = "pi_123",
                merchantAccountId = "m_1",
                amountValue = 1000L,
                currency = "EUR",
                now = now
            )
            
            // when
            val envelope: EventEnvelope<CaptureReceived> = EventEnvelopeFactory.envelopeFor(
                data = event,
                aggregateId = event.publicPaymentIntentId,
                traceId = traceIdFromMDC
            )

            // then
            Assertions.assertThat(envelope.eventId).isNotNull
            Assertions.assertThat(envelope.traceId).isEqualTo(traceIdFromMDC)
            Assertions.assertThat(envelope.eventType).isEqualTo("capture_received")
        } finally {
            MDC.clear()
        }
    }

    @Test
    fun `should generate new traceId when not present in MDC`() {
        // when
        assertThrows<IllegalStateException> {
            val now = Utc.nowInstant()
            val event = CaptureReceived.from(
                captureTxId = 1L,
                paymentIntentId = "1001",
                publicPaymentIntentId = "pi_123",
                merchantAccountId = "m_1",
                amountValue = 1000L,
                currency = "EUR",
                now = now
            )
            
            EventEnvelopeFactory.envelopeFor(
                data = event,
                aggregateId = event.publicPaymentIntentId,
                traceId = MDC.get(GenericLogFields.TRACE_ID) ?: throw IllegalStateException("TraceId missing")
            )
        }
    }

    @Test
    fun `should generate unique eventId and same traceid each time`() {
        val traceIdFromMDC = "test-traceid"
        MDC.put(GenericLogFields.TRACE_ID, traceIdFromMDC)
        try {
            val now = Utc.nowInstant()
            val event1 = CaptureReceived.from(
                captureTxId = 1L,
                paymentIntentId = "1001",
                publicPaymentIntentId = "pi_123",
                merchantAccountId = "m_1",
                amountValue = 1000L,
                currency = "EUR",
                now = now
            )
            val envelope1 = EventEnvelopeFactory.envelopeFor(
                data = event1,
                aggregateId = event1.publicPaymentIntentId,
                traceId = traceIdFromMDC
            )
            
            val event2 = CaptureReceived.from(
                captureTxId = 2L,
                paymentIntentId = "1002",
                publicPaymentIntentId = "pi_124",
                merchantAccountId = "m_1",
                amountValue = 1000L,
                currency = "EUR",
                now = now
            )
            val envelope2 = EventEnvelopeFactory.envelopeFor(
                data = event2,
                aggregateId = event2.publicPaymentIntentId,
                traceId = traceIdFromMDC
            )
            
            Assertions.assertThat(envelope1.traceId).isEqualTo(envelope2.traceId)
            Assertions.assertThat(envelope1.eventId).isNotEqualTo(envelope2.eventId)

        } finally {
            MDC.clear()
        }
    }

    @Test
    fun `envelopeFor should create EventEnvelope with correct fields`() {

        val traceIdFromMDC = "test-traceid"
        MDC.put(GenericLogFields.TRACE_ID, traceIdFromMDC)
        try {
            val now = Utc.nowInstant()
            val event = CaptureReceived.from(
                captureTxId = 1L,
                paymentIntentId = "1001",
                publicPaymentIntentId = "pi_123",
                merchantAccountId = "m_1",
                amountValue = 1000L,
                currency = "EUR",
                now = now
            )

            val envelope = EventEnvelopeFactory.envelopeFor(
                data = event,
                aggregateId = event.publicPaymentIntentId,
                traceId = traceIdFromMDC
            )

            Assertions.assertThat(envelope.traceId).isNotBlank
            Assertions.assertThat(envelope.eventId).isNotNull
            Assertions.assertThat(envelope.eventType).isEqualTo("capture_received")
            Assertions.assertThat(envelope.aggregateId).isEqualTo(event.publicPaymentIntentId)
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
            val now = Utc.nowInstant()
            val event = CaptureReceived.from(
                captureTxId = 1L,
                paymentIntentId = "1001",
                publicPaymentIntentId = "pi_123",
                merchantAccountId = "m_1",
                amountValue = 1000L,
                currency = "EUR",
                now = now
            )

            val envelope = EventEnvelopeFactory.envelopeFor(
                data = event,
                aggregateId = event.publicPaymentIntentId,
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