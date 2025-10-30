package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.model.LedgerEntry
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.ledger.*
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
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
import kotlin.test.assertTrue

class RecordLedgerEntriesServiceLedgerContentTest {

    private lateinit var ledgerWritePort: LedgerEntryPort
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var clock: Clock
    private lateinit var service: RecordLedgerEntriesService

    @BeforeEach
    fun setup() {
        ledgerWritePort = mockk(relaxed = true)
        eventPublisherPort = mockk(relaxed = true)
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)
        service = RecordLedgerEntriesService(ledgerWritePort, eventPublisherPort, clock)
    }

    private fun sampleCommand(status: String = "SUCCESSFUL_FINAL") = LedgerRecordingCommand(
        paymentOrderId = "po-123",
        publicPaymentOrderId = "paymentorder-123",
        paymentId = "p-456",
        publicPaymentId = "payment-456",
        sellerId = "seller-789",
        amountValue = 10_000L,
        currency = "EUR",
        status = status,
        createdAt = LocalDateTime.now(clock),
        updatedAt = LocalDateTime.now(clock)
    )

    @Test
    fun `should persist correct ledger entries and postings for SUCCESSFUL_FINAL`() {
        // given - a LedgerRecordingCommand
        val command = sampleCommand("SUCCESSFUL_FINAL")
        val expectedEventId = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111")
        val expectedTraceId = "trace-111"
        
        // Mock LogContext to control traceId and parentEventId
        mockkObject(LogContext)
        every { LogContext.getEventId() } returns expectedEventId
        every { LogContext.getTraceId() } returns expectedTraceId

        // Setup mocks to accept calls
        every { ledgerWritePort.appendLedgerEntry(any()) } returns Unit
        every { eventPublisherPort.publishSync<LedgerEntriesRecorded>(any(), any(), any(), any(), any()) } returns mockk()

        // when - service processes the command
        service.recordLedgerEntries(command)

        // then - verify SUCCESSFUL_FINAL creates 5 journal entries with exact structure
        // Expected: 1. AUTH_HOLD, 2. CAPTURE, 3. SETTLEMENT, 4. FEE, 5. PAYOUT
        
        // Verify append was called 5 times and validate content via capture
        val captured = mutableListOf<LedgerEntry>()
        verify(exactly = 5) { ledgerWritePort.appendLedgerEntry(capture(captured)) }
        assertEquals(5, captured.size)
        // spot-check a few key expectations
        assertEquals("AUTH:paymentorder-123", captured[0].journalEntry.id)
        assertEquals(JournalType.AUTH_HOLD, captured[0].journalEntry.txType)
        assertEquals("CAPTURE:paymentorder-123", captured[1].journalEntry.id)
        assertEquals(JournalType.CAPTURE, captured[1].journalEntry.txType)
        assertEquals("PAYOUT:paymentorder-123", captured[4].journalEntry.id)
        assertEquals(JournalType.PAYOUT, captured[4].journalEntry.txType)

        // then - verify publishSync called with exact parameters
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.sellerId,
                data = match { event ->
                    event is LedgerEntriesRecorded &&
                    event.ledgerBatchId.startsWith("ledger-batch-") &&
                    event.entryCount == 5 && // fullFlow creates 5 entries
                    event.publicPaymentOrderId == command.publicPaymentOrderId &&
                    event.paymentOrderId == command.paymentOrderId &&
                    event.sellerId == command.sellerId &&
                    event.currency == command.currency &&
                    event.status == command.status &&
                    event.traceId == expectedTraceId &&
                    event.parentEventId == expectedEventId.toString()
                },
                parentEventId = expectedEventId,
                traceId = expectedTraceId
            )
        }
    }
    @Test
    fun `should not persist or publish for FAILED_FINAL`() {
        val command = sampleCommand("FAILED_FINAL")

        service.recordLedgerEntries(command)

        // 1️⃣ No ledger entries should be persisted
        verify { ledgerWritePort wasNot Called }

        // 2️⃣ No publishSync should be invoked at all
        verify(exactly = 0) {
            eventPublisherPort.publishSync<LedgerEntriesRecorded>(
                eventMetaData = any(),
                aggregateId = any(),
                data = any(),
                parentEventId = any(),
                traceId = any()
            )
        }
    }

    @Test
    fun `should not persist or publish for unknown status`() {
        val command = sampleCommand("UNKNOWN_STATUS")

        service.recordLedgerEntries(command)

        // 1️⃣ No ledger entries should be persisted
        verify { ledgerWritePort wasNot Called }

        // 2️⃣ No publishSync should be invoked
        verify(exactly = 0) {
            eventPublisherPort.publishSync<LedgerEntriesRecorded>(
                eventMetaData = any(),
                aggregateId = any(),
                data = any(),
                parentEventId = any(),
                traceId = any()
            )
        }
    }

    @Test
    fun `should handle exception in postLedgerEntriesAtomic and not publish event`() {
        // Given
        val command = sampleCommand("SUCCESSFUL_FINAL")
        
        every { ledgerWritePort.appendLedgerEntry(any()) } throws RuntimeException("Database write failed")

        // When/Then - Should propagate exception before publishing
        assertThrows<RuntimeException> {
            service.recordLedgerEntries(command)
        }

        // Verify that append was attempted
        verify(atLeast = 1) { ledgerWritePort.appendLedgerEntry(any()) }
        
        // Verify that publishSync was NOT called (exception before publishing)
        verify(exactly = 0) {
            eventPublisherPort.publishSync<LedgerEntriesRecorded>(
                eventMetaData = any(),
                aggregateId = any(),
                data = any(),
                parentEventId = any(),
                traceId = any()
            )
        }
    }

    @Test
    fun `should handle exception in publishSync and not crash`() {
        // Given
        val command = sampleCommand("SUCCESSFUL_FINAL")
        val capturedEntries = mutableListOf<LedgerEntry>()
        val capturedEvent = slot<LedgerEntriesRecorded>()
        
        every { ledgerWritePort.appendLedgerEntry(capture(capturedEntries)) } returns Unit
        every {
            eventPublisherPort.publishSync(
                eventMetaData = any(),
                aggregateId = any(),
                data = capture(capturedEvent),
                parentEventId = any(),
                traceId = any()
            )
        } throws RuntimeException("Event publish error")

        // When/Then - Should propagate exception
        assertThrows<RuntimeException> {
            service.recordLedgerEntries(command)
        }

        // Verify that some entries were persisted before exception
        assertTrue(capturedEntries.isNotEmpty())
        
        // Verify that event data was being sent before exception
        assertNotNull(capturedEvent.captured)
        assertTrue(capturedEvent.captured.ledgerBatchId.startsWith("ledger-batch-"))
        assertEquals(command.publicPaymentOrderId, capturedEvent.captured.publicPaymentOrderId)
    }

    @Test
    fun `should verify explicit parameters for ledger entries recording`() {
        // Given - Verify all entries are persisted correctly
        val command = sampleCommand("SUCCESSFUL_FINAL")
        
        every { ledgerWritePort.appendLedgerEntry(any()) } returns Unit
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.sellerId,
                data = any(),
                parentEventId = any(),
                traceId = any()
            )
        } returns mockk()

        // When
        service.recordLedgerEntries(command)

        // Then - Verify append was called 5 times
        verify(exactly = 5) { ledgerWritePort.appendLedgerEntry(any()) }

        // Then - Verify it was called
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.sellerId,
                data = match { it is LedgerEntriesRecorded },
                parentEventId = any(),
                traceId = any()
            )
        }
    }
}