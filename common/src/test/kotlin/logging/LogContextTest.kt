package logging

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.GenericLogFields
import com.dogancaglar.common.logging.LogContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.*

class LogContextTest {

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun `should populate MDC for envelope and restore previous context`() {
        // given – an outer trace already in MDC (simulates a nested scope)
        MDC.put(GenericLogFields.TRACE_ID, "outer-trace")

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
            // inside scope
            assertThat(MDC.get(GenericLogFields.TRACE_ID)).isEqualTo("trace-123")
            assertThat(MDC.get(GenericLogFields.EVENT_ID)).isEqualTo(envelope.eventId.toString())
            assertThat(MDC.get(GenericLogFields.AGGREGATE_ID)).isEqualTo("payment-123")
        }

        // then – previous value should be restored, not cleared
        assertThat(MDC.get(GenericLogFields.TRACE_ID)).isEqualTo("outer-trace")
        assertThat(MDC.get(GenericLogFields.EVENT_ID)).isNull()
        assertThat(MDC.get(GenericLogFields.AGGREGATE_ID)).isNull()
    }
}