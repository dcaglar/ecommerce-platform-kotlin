package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.domain.event.PaymentOrderFailed
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RequestLedgerRecordingServiceTest {

    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var clock: Clock
    private lateinit var service: RequestLedgerRecordingService

    @BeforeEach
    fun setup() {
        eventPublisherPort = mockk(relaxed = true)
        clock = Clock.fixed(Instant.parse("2025-01-01T10:00:00Z"), ZoneOffset.UTC)
        service = RequestLedgerRecordingService(eventPublisherPort, clock)
    }

    // Helper method to create PaymentOrderSucceeded with SUCCESSFUL_FINAL status
    private fun createPaymentOrderSucceeded(
        paymentOrderId: String = "po-123",
        publicPaymentOrderId: String = "paymentorder-123",
        paymentId: String = "p-456",
        publicPaymentId: String = "payment-456",
        sellerId: String = "seller-789",
        amountValue: Long = 10000L,
        currency: String = "EUR",
        retryCount: Int = 0
    ): PaymentOrderSucceeded = PaymentOrderSucceeded.create(
        paymentOrderId = paymentOrderId,
        publicPaymentOrderId = publicPaymentOrderId,
        paymentId = paymentId,
        publicPaymentId = publicPaymentId,
        sellerId = sellerId,
        amountValue = amountValue,
        currency = currency,
        status = "SUCCESSFUL_FINAL",
        createdAt = LocalDateTime.now(clock),
        updatedAt = LocalDateTime.now(clock),
        retryCount = retryCount
    )

    // Helper method to create PaymentOrderFailed with FAILED_FINAL status
    private fun createPaymentOrderFailed(
        paymentOrderId: String = "po-999",
        publicPaymentOrderId: String = "paymentorder-999",
        paymentId: String = "p-999",
        publicPaymentId: String = "payment-999",
        sellerId: String = "seller-789",
        amountValue: Long = 5000L,
        currency: String = "EUR",
        retryCount: Int = 0,
        retryReason: String? = null,
        lastErrorMessage: String? = null
    ): PaymentOrderFailed = PaymentOrderFailed.create(
        paymentOrderId = paymentOrderId,
        publicPaymentOrderId = publicPaymentOrderId,
        paymentId = paymentId,
        publicPaymentId = publicPaymentId,
        sellerId = sellerId,
        amountValue = amountValue,
        currency = currency,
        status = "FAILED_FINAL",
        createdAt = LocalDateTime.now(clock),
        updatedAt = LocalDateTime.now(clock),
        retryCount = retryCount
    )
    @Test
    fun `should publish LedgerRecordingCommand for PaymentOrderSucceeded with SUCCESSFUL_FINAL status`() {
        // given - PaymentOrderSucceeded with SUCCESSFUL_FINAL status
        val event = createPaymentOrderSucceeded()
        val expectedEventId = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111")
        val expectedTraceId = "trace-111"
        
        // Mock LogContext to control traceId and parentEventId
        mockkObject(LogContext)
        every { LogContext.getEventId() } returns expectedEventId
        every { LogContext.getTraceId() } returns expectedTraceId
        
        // Setup mocks to accept calls
        every { eventPublisherPort.publishSync<LedgerRecordingCommand>(any(), any(), any(), any(), any()) } returns mockk()

        // when - service processes the event
        service.requestLedgerRecording(event)

        // then - verify publishSync called with exact parameters and correct status
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = event.sellerId,
                data = match { cmd ->
                    cmd is LedgerRecordingCommand &&
                    cmd.paymentOrderId == event.paymentOrderId &&
                    cmd.paymentId == event.paymentId &&
                    cmd.sellerId == event.sellerId &&
                    cmd.amountValue == event.amountValue &&
                    cmd.currency == event.currency &&
                    cmd.status == "SUCCESSFUL_FINAL" &&
                    cmd.createdAt == LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
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

        val expectedEventId = java.util.UUID.fromString("22222222-2222-2222-2222-222222222222")
        val expectedTraceId = "trace-222"
        
        // Mock LogContext to control traceId and parentEventId
        mockkObject(LogContext)
        every { LogContext.getEventId() } returns expectedEventId
        every { LogContext.getTraceId() } returns expectedTraceId
        
        // Setup mocks to accept calls
        every { eventPublisherPort.publishSync<LedgerRecordingCommand>(any(), any(), any(), any(), any()) } returns mockk()

        // when - service processes the event
        service.requestLedgerRecording(failedEvent)

        // then - verify publishSync called with exact parameters and FAILED_FINAL status
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = failedEvent.sellerId,
                data = match { cmd ->
                    cmd is LedgerRecordingCommand &&
                    cmd.status == "FAILED_FINAL" &&
                    cmd.paymentOrderId == failedEvent.paymentOrderId &&
                    cmd.paymentId == failedEvent.paymentId &&
                    cmd.sellerId == failedEvent.sellerId &&
                    cmd.amountValue == failedEvent.amountValue &&
                    cmd.currency == failedEvent.currency &&
                    cmd.createdAt == LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                },
                parentEventId = expectedEventId,
                traceId = expectedTraceId
            )
        }
    }
    @Test
    fun `should handle exception in publishSync and propagate it for PaymentOrderSucceeded`() {
        // given - PaymentOrderSucceeded event
        val event = createPaymentOrderSucceeded()
        val capturedCommand = slot<LedgerRecordingCommand>()
        
        every {
            eventPublisherPort.publishSync(
                eventMetaData = any(),
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
        assertEquals("SUCCESSFUL_FINAL", capturedCommand.captured.status)
    }

    @Test
    fun `should skip publishing for PaymentOrderSucceeded type with invalid status`() {
        // given - Event that looks like PaymentOrderSucceeded but has wrong status (PENDING_STATUS_CHECK_LATER)
        val eventWithInvalidStatus = object : PaymentOrderEvent {
            override val paymentOrderId = "po-invalid-success"
            override val publicPaymentOrderId = "paymentorder-invalid-success"
            override val paymentId = "p-invalid-success"
            override val publicPaymentId = "payment-invalid-success"
            override val sellerId = "seller-invalid"
            override val amountValue = 15000L
            override val currency = "USD"
            override val status = "PENDING_STATUS_CHECK_LATER"  // Invalid status for succeeded event
            override val createdAt = LocalDateTime.now(clock)
            override val updatedAt = LocalDateTime.now(clock)
            override val retryCount = 0
        }
        
        // when - service processes the event
        service.requestLedgerRecording(eventWithInvalidStatus)

        // then - verify publishSync was NOT called because status is not final
        verify(exactly = 0) {
            eventPublisherPort.publishSync<LedgerRecordingCommand>(
                eventMetaData = any(),
                aggregateId = any(),
                data = any(),
                parentEventId = any(),
                traceId = any()
            )
        }
    }

    @Test
    fun `should skip publishing for PaymentOrderFailed type with invalid status`() {
        // given - Event that looks like PaymentOrderFailed but has wrong status (FAILED_TRANSIENT_ERROR)
        val eventWithInvalidStatus = object : PaymentOrderEvent {
            override val paymentOrderId = "po-invalid-failed"
            override val publicPaymentOrderId = "paymentorder-invalid-failed"
            override val paymentId = "p-invalid-failed"
            override val publicPaymentId = "payment-invalid-failed"
            override val sellerId = "seller-invalid"
            override val amountValue = 7000L
            override val currency = "GBP"
            override val status = "FAILED_TRANSIENT_ERROR"  // Invalid status (not FAILED_FINAL)
            override val createdAt = LocalDateTime.now(clock)
            override val updatedAt = LocalDateTime.now(clock)
            override val retryCount = 0
        }
        
        // when - service processes the event
        service.requestLedgerRecording(eventWithInvalidStatus)

        // then - verify publishSync was NOT called because status is not final
        verify(exactly = 0) {
            eventPublisherPort.publishSync<LedgerRecordingCommand>(
                eventMetaData = any(),
                aggregateId = any(),
                data = any(),
                parentEventId = any(),
                traceId = any()
            )
        }
    }
}