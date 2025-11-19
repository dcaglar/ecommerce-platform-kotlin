package logging

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import junit.framework.TestCase.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.time.LocalDateTime
import kotlin.test.assertEquals

class EventLogContextTest {

    data class TestEvent(
        override val eventType: String = "x",
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : Event {
        override fun deterministicEventId() = "id-x"
    }

    @Test
    fun `with(EventEnvelope) populates and restores MDC`() {
        val env = EventEnvelopeFactory.envelopeFor(
            TestEvent(),
            aggregateId = "agg-1",
            traceId = "trace-1"
        )

        assertTrue(MDC.getCopyOfContextMap()?.isEmpty() ?: true)

        EventLogContext.with(env) {
            assertEquals("trace-1", MDC.get("traceId"))
            assertEquals("agg-1", MDC.get("aggregateId"))
        }

        assertTrue(MDC.getCopyOfContextMap()?.isEmpty() ?: true)
    }

    @Test
    fun `nested with calls override and then restore MDC`() {
        val e1 = EventEnvelopeFactory.envelopeFor(TestEvent(), "A", "T1")
        val e2 = EventEnvelopeFactory.envelopeFor(TestEvent(), "B", "T2")

        EventLogContext.with(e1) {
            assertEquals("T1", MDC.get("traceId"))

            EventLogContext.with(e2) {
                assertEquals("T2", MDC.get("traceId"))
            }

            // restored
            assertEquals("T1", MDC.get("traceId"))
        }
    }

    @Test
    fun `withRetryFields sets retry fields and restores`() {
        val env = EventEnvelopeFactory.envelopeFor(TestEvent(), "A", "T")
        EventLogContext.with(env) {
            EventLogContext.withRetryFields(
                retryCount = 3,
                retryReason = "timeout",
                backOffInMillis = 500L,
            ) {
                assertEquals("3", MDC.get("retryCount"))
                assertEquals("timeout", MDC.get("retryReason"))
            }

            // restored to envelope values
            assertEquals("T", MDC.get("traceId"))
        }
    }
}