package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent
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

    private fun samplePaymentOrderEvent(): PaymentOrderEvent = object : PaymentOrderEvent {
        override val paymentOrderId = "po-123"
        override val publicPaymentOrderId = "paymentorder-123"
        override val paymentId = "p-456"
        override val publicPaymentId = "payment-456"
        override val sellerId = "seller-789"
        override val amountValue = 10000L
        override val currency = "EUR"
        override val status = "SUCCESSFUL_FINAL"
        override val createdAt = LocalDateTime.now(clock)
        override val updatedAt = LocalDateTime.now(clock)
        override val retryCount = 0
        override val retryReason = null
        override val lastErrorMessage = null
    }

    @Test
    fun `should publish LedgerRecordingCommand for SUCCESSFUL_FINAL status`() {
        // given - PaymentOrderEvent with SUCCESSFUL_FINAL status
        val event = samplePaymentOrderEvent()
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

        // then - verify publishSync called with exact parameters
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = event.sellerId,
                data = match { cmd ->
                    cmd is LedgerRecordingCommand &&
                    cmd.paymentOrderId == event.paymentOrderId &&
                    cmd.publicPaymentOrderId == event.publicPaymentOrderId &&
                    cmd.paymentId == event.paymentId &&
                    cmd.publicPaymentId == event.publicPaymentId &&
                    cmd.sellerId == event.sellerId &&
                    cmd.amountValue == event.amountValue &&
                    cmd.currency == event.currency &&
                    cmd.status == event.status &&
                    cmd.createdAt == LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                },
                parentEventId = expectedEventId,
                traceId = expectedTraceId
            )
        }
    }

    @Test
    fun `should publish LedgerRecordingCommand for FAILED_FINAL status`() {
        // given - PaymentOrderEvent with FAILED_FINAL status
        val failedEvent = object : PaymentOrderEvent {
            override val paymentOrderId = "po-999"
            override val publicPaymentOrderId = "paymentorder-999"
            override val paymentId = "p-999"
            override val publicPaymentId = "payment-999"
            override val sellerId = "seller-789"
            override val amountValue = 5000L
            override val currency = "EUR"
            override val status = "FAILED_FINAL"
            override val createdAt = LocalDateTime.now(clock)
            override val updatedAt = LocalDateTime.now(clock)
            override val retryCount = 0
            override val retryReason = null
            override val lastErrorMessage = null
        }

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

        // then - verify publishSync called with exact parameters
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = failedEvent.sellerId,
                data = match { cmd ->
                    cmd is LedgerRecordingCommand &&
                    cmd.status == "FAILED_FINAL" &&
                    cmd.paymentOrderId == failedEvent.paymentOrderId &&
                    cmd.publicPaymentOrderId == failedEvent.publicPaymentOrderId &&
                    cmd.paymentId == failedEvent.paymentId &&
                    cmd.publicPaymentId == failedEvent.publicPaymentId &&
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
    fun `should skip publishing for non-final status`() {
        // given - PaymentOrderEvent with non-final status
        val eventWithNonFinalStatus = object : PaymentOrderEvent {
            override val paymentOrderId = "po-888"
            override val publicPaymentOrderId = "paymentorder-888"
            override val paymentId = "p-888"
            override val publicPaymentId = "payment-888"
            override val sellerId = "seller-123"
            override val amountValue = 3000L
            override val currency = "USD"
            override val status = "PENDING"
            override val createdAt = LocalDateTime.now(clock)
            override val updatedAt = LocalDateTime.now(clock)
            override val retryCount = 0
            override val retryReason = null
            override val lastErrorMessage = null
        }
        
        // when - service processes the event
        service.requestLedgerRecording(eventWithNonFinalStatus)

        // then - verify publishSync was NOT called (skipped for non-final status)
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
    fun `should handle exception in publishSync and propagate it`() {
        // given
        val event = samplePaymentOrderEvent()
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
        assertEquals(event.publicPaymentOrderId, capturedCommand.captured.publicPaymentOrderId)
        assertEquals(event.status, capturedCommand.captured.status)
    }
}