package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import io.mockk.*
import com.dogancaglar.common.time.Utc
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RequestLedgerRecordingServiceTest {

    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var service: RequestLedgerRecordingService

    @BeforeEach
    fun setup() {
        eventPublisherPort = mockk(relaxed = true)
        service = RequestLedgerRecordingService(eventPublisherPort)
    }

    // Helper method to create PaymentOrderFinalized with SUCCESSFUL_FINAL status
    private fun createPaymentOrderSucceeded(
        paymentOrderId: String = "123",
        paymentId: String = "456",
        sellerId: String = "seller-789",
        amountValue: Long = 10000L,
        currency: String = "EUR"
    ): PaymentOrderFinalized = PaymentOrderFinalized.fromJson(
        pOrderId = paymentOrderId,
        pubOrderId = "paymentorder-$paymentOrderId",
        pId = paymentId,
        pubPId = "payment-$paymentId",
        sellerId = sellerId,
        status = "SUCCESSFUL_FINAL",
        amount = amountValue,
        currency = currency,
        timestamp = Utc.nowInstant()
    )

    // Helper method to create PaymentOrderFinalized with FAILED_FINAL status
    private fun createPaymentOrderFailed(
        paymentOrderId: String = "999",
        paymentId: String = "999",
        sellerId: String = "seller-789",
        amountValue: Long = 5000L,
        currency: String = "EUR"
    ): PaymentOrderFinalized = PaymentOrderFinalized.fromJson(
        pOrderId = paymentOrderId,
        pubOrderId = "paymentorder-$paymentOrderId",
        pId = paymentId,
        pubPId = "payment-$paymentId",
        sellerId = sellerId,
        status = "FAILED_FINAL",
        amount = amountValue,
        currency = currency,
        timestamp = Utc.nowInstant()
    )
    @Test
    fun `should publish LedgerRecordingCommand for PaymentOrderSucceeded with SUCCESSFUL_FINAL status`() {
        // given - PaymentOrderFinalized with SUCCESSFUL_FINAL status
        val event = createPaymentOrderSucceeded()
        val expectedEventId = "11111111-1111-1111-1111-111111111111"
        val expectedTraceId = "trace-111"
        
        // Mock EventLogContext to control traceId and parentEventId
        mockkObject(EventLogContext)
        every { EventLogContext.getEventId() } returns expectedEventId
        every { EventLogContext.getTraceId() } returns expectedTraceId
        
        // Setup mocks to accept calls
        every { eventPublisherPort.publishSync<LedgerRecordingCommand>(any(), any(), any(), any(), any()) } returns mockk()

        // when - service processes the event
        service.requestLedgerRecording(event)

        // then - verify publishSync called with exact parameters and correct status
        val now = Utc.nowInstant()
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                aggregateId = event.sellerId,
                data = match { cmd ->
                    cmd is LedgerRecordingCommand &&
                    cmd.paymentOrderId == event.paymentOrderId &&
                    cmd.paymentId == event.paymentId &&
                    cmd.sellerId == event.sellerId &&
                    cmd.amountValue == event.amountValue &&
                    cmd.currency == event.currency &&
                    cmd.finalStatus == event.eventType &&
                    cmd.timestamp.isAfter(now.minusSeconds(5)) &&
                    cmd.timestamp.isBefore(now.plusSeconds(5))
                },
                parentEventId = expectedEventId,
                traceId = expectedTraceId
            )
        }
    }

    @Test
    fun `should publish LedgerRecordingCommand for PaymentOrderFailed with FAILED_FINAL status`() {
        // given - PaymentOrderFailed with FAILED_FINAL status
        val failedEvent = createPaymentOrderFailed()

        val expectedEventId = "22222222-2222-2222-2222-222222222222"
        val expectedTraceId = "trace-222"
        
        // Mock EventLogContext to control traceId and parentEventId
        mockkObject(EventLogContext)
        every { EventLogContext.getEventId() } returns expectedEventId
        every { EventLogContext.getTraceId() } returns expectedTraceId
        
        // Setup mocks to accept calls
        every { eventPublisherPort.publishSync<LedgerRecordingCommand>(any(), any(), any(), any(), any()) } returns mockk()

        // when - service processes the event
        service.requestLedgerRecording(failedEvent)

        // then - verify publishSync called with exact parameters and FAILED_FINAL status
        val now = Utc.nowInstant()
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                aggregateId = failedEvent.sellerId,
                data = match { cmd ->
                    cmd is LedgerRecordingCommand &&
                    cmd.finalStatus == failedEvent.eventType &&
                    cmd.paymentOrderId == failedEvent.paymentOrderId &&
                    cmd.paymentId == failedEvent.paymentId &&
                    cmd.sellerId == failedEvent.sellerId &&
                    cmd.amountValue == failedEvent.amountValue &&
                    cmd.currency == failedEvent.currency &&
                    cmd.timestamp.isAfter(now.minusSeconds(5)) &&
                    cmd.timestamp.isBefore(now.plusSeconds(5))
                },
                parentEventId = expectedEventId,
                traceId = expectedTraceId
            )
        }
    }
    @Test
    fun `should handle exception in publishSync and propagate it for PaymentOrderSucceeded`() {
        // given - PaymentOrderFinalized event
        val event = createPaymentOrderSucceeded()
        val capturedCommand = slot<LedgerRecordingCommand>()
        
        every {
            eventPublisherPort.publishSync(
                aggregateId = any(),
                data = capture(capturedCommand),
                parentEventId = any(),
                traceId = any()
            )
        } throws RuntimeException("Kafka publish error")

        // when/then - Should propagate exception
        assertThrows<RuntimeException> {
            service.requestLedgerRecording(event)
        }

        // then - verify publishSync was attempted with correct data before exception
        assertNotNull(capturedCommand.captured)
        assertEquals(event.paymentOrderId, capturedCommand.captured.paymentOrderId)
        assertEquals(event.eventType, capturedCommand.captured.finalStatus)
    }

    // Note: The requestLedgerRecording method only accepts PaymentOrderFinalized,
    // so we cannot test with non-finalized events at compile time.
    // The type system ensures only finalized events can trigger ledger recording.
}