package com.dogancaglar.common.logging

import com.dogancaglar.common.event.EventEnvelope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.*

class LogContextTest {

    @Test
    fun `should populate and clear MDC correctly for envelope`() {
        // given
        val envelope = EventEnvelope(
            traceId = "trace-123",
            eventId = UUID.randomUUID(),
            parentEventId = UUID.randomUUID(),
            eventType = "payment_created",
            aggregateId = "payment-123",
            data = "dummy"
        )

        // when
        LogContext.with(envelope) {
            assertThat(MDC.get(LogFields.TRACE_ID)).isEqualTo("trace-123")
            assertThat(MDC.get(LogFields.EVENT_ID)).isEqualTo(envelope.eventId.toString())
            assertThat(MDC.get(LogFields.AGGREGATE_ID)).isEqualTo("payment-123")
            assertThat(MDC.get(LogFields.EVENT_TYPE)).isEqualTo("payment_created")
        }

        // then
        // MDC should be cleared outside of LogContext scope
        assertThat(MDC.get(LogFields.TRACE_ID)).isNull()
        assertThat(MDC.get(LogFields.EVENT_ID)).isNull()
        assertThat(MDC.get(LogFields.AGGREGATE_ID)).isNull()
        assertThat(MDC.get(LogFields.EVENT_TYPE)).isNull()
    }
}