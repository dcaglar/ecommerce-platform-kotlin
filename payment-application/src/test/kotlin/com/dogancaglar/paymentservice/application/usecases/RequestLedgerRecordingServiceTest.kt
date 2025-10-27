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
    fun `should publish LedgerRecordingCommand with correct data and explicit parameters`() {
        // given
        val event = samplePaymentOrderEvent()
        val capturedCommand = slot<LedgerRecordingCommand>()
        
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = event.publicPaymentOrderId,
                data = capture(capturedCommand),
                parentEventId = LogContext.getEventId(),
                traceId = LogContext.getTraceId()
            )
        } returns mockk()

        // when
        service.requestLedgerRecording(event)

        // then
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = event.publicPaymentOrderId,
                data = match { it is LedgerRecordingCommand },  // More explicit than any()
                parentEventId = LogContext.getEventId(),
                traceId = LogContext.getTraceId()
            )
        }

        val cmd = capturedCommand.captured

        // assert field mapping
        assertEquals(event.paymentOrderId, cmd.paymentOrderId)
        assertEquals(event.publicPaymentOrderId, cmd.publicPaymentOrderId)
        assertEquals(event.paymentId, cmd.paymentId)
        assertEquals(event.publicPaymentId, cmd.publicPaymentId)
        assertEquals(event.sellerId, cmd.sellerId)
        assertEquals(event.amountValue, cmd.amountValue)
        assertEquals(event.currency, cmd.currency)
        assertEquals(event.status, cmd.status)

        // assert clock usage
        assertEquals(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), cmd.createdAt)
    }

    @Test
    fun `should publish LedgerRecordingCommand with FAILED_FINAL status and explicit parameters`() {
        // given
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

        val capturedCommand = slot<LedgerRecordingCommand>()
        
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = failedEvent.publicPaymentOrderId,
                data = capture(capturedCommand),
                parentEventId = LogContext.getEventId(),
                traceId = LogContext.getTraceId()
            )
        } returns mockk()

        // when
        service.requestLedgerRecording(failedEvent)

        // then
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = failedEvent.publicPaymentOrderId,
                data = match { it is LedgerRecordingCommand },
                parentEventId = LogContext.getEventId(),
                traceId = LogContext.getTraceId()
            )
        }

        val cmd = capturedCommand.captured
        assertEquals("FAILED_FINAL", cmd.status)
        assertEquals(failedEvent.publicPaymentOrderId, cmd.publicPaymentOrderId)
        assertEquals(failedEvent.paymentId, cmd.paymentId)
        assertEquals(failedEvent.publicPaymentId, cmd.publicPaymentId)
        assertEquals(failedEvent.sellerId, cmd.sellerId)
        assertEquals(failedEvent.amountValue, cmd.amountValue)
        assertEquals(failedEvent.currency, cmd.currency)
    }

    @Test
    fun `should handle exception in publishSync and not crash`() {
        // Given
        val event = samplePaymentOrderEvent()
        val capturedCommand = slot<LedgerRecordingCommand>()
        
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = event.publicPaymentOrderId,
                data = capture(capturedCommand),
                parentEventId = LogContext.getEventId(),
                traceId = LogContext.getTraceId()
            )
        } throws RuntimeException("Kafka publish error")

        // When/Then - Should propagate exception
        assertThrows<RuntimeException> {
            service.requestLedgerRecording(event)
        }

        // Verify that publishSync was attempted with correct data before exception
        assertNotNull(capturedCommand.captured)
        assertEquals(event.publicPaymentOrderId, capturedCommand.captured.publicPaymentOrderId)
        assertEquals(event.status, capturedCommand.captured.status)
    }

    @Test
    fun `should handle empty event data correctly`() {
        // Given
        val eventWithEmptyData = object : PaymentOrderEvent {
            override val paymentOrderId = "po-999"
            override val publicPaymentOrderId = "paymentorder-999"
            override val paymentId = "p-999"
            override val publicPaymentId = "payment-999"
            override val sellerId = ""
            override val amountValue = 0L
            override val currency = ""
            override val status = "SUCCESSFUL_FINAL"
            override val createdAt = LocalDateTime.now(clock)
            override val updatedAt = LocalDateTime.now(clock)
            override val retryCount = 0
            override val retryReason = null
            override val lastErrorMessage = null
        }

        val capturedCommand = slot<LedgerRecordingCommand>()
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = eventWithEmptyData.publicPaymentOrderId,
                data = capture(capturedCommand),
                parentEventId = LogContext.getEventId(),
                traceId = LogContext.getTraceId()
            )
        } returns mockk()

        // When
        service.requestLedgerRecording(eventWithEmptyData)

        // Then - Should still publish with empty values
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
                aggregateId = eventWithEmptyData.publicPaymentOrderId,
                data = match { it is LedgerRecordingCommand },
                parentEventId = LogContext.getEventId(),
                traceId = LogContext.getTraceId()
            )
        }

        val cmd = capturedCommand.captured
        assertEquals("", cmd.sellerId)
        assertEquals(0L, cmd.amountValue)
        assertEquals("", cmd.currency)
    }
}