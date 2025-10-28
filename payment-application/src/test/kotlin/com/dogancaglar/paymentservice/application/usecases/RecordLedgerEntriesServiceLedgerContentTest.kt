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
        every { ledgerWritePort.postLedgerEntriesAtomic(any()) } returns Unit
        every { eventPublisherPort.publishSync<LedgerEntriesRecorded>(any(), any(), any(), any(), any()) } returns mockk()

        // when - service processes the command
        service.recordLedgerEntries(command)

        // then - verify SUCCESSFUL_FINAL creates 5 journal entries with exact structure
        // Expected: 1. AUTH_HOLD, 2. CAPTURE, 3. SETTLEMENT, 4. FEE, 5. PAYOUT
        
        // Verify batch was called with 5 entries
        verify(exactly = 1) {
            ledgerWritePort.postLedgerEntriesAtomic(
                match { entries ->
                    entries.size == 5 &&
                    // 1. AUTH_HOLD entry
                    entries[0].ledgerEntryId == 0L &&
                    entries[0].createdAt != null &&
                    entries[0].journalEntry.id == "AUTH:paymentorder-123" &&
                    entries[0].journalEntry.txType == JournalType.AUTH_HOLD &&
                    entries[0].journalEntry.name == "Authorization Hold" &&
                    entries[0].journalEntry.postings.size == 2 &&
                    (entries[0].journalEntry.postings[0] as Posting.Debit).account.accountType == AccountType.AUTH_RECEIVABLE &&
                    (entries[0].journalEntry.postings[0] as Posting.Debit).amount.value == 10000L &&
                    (entries[0].journalEntry.postings[0] as Posting.Debit).amount.currency == "EUR" &&
                    (entries[0].journalEntry.postings[1] as Posting.Credit).account.accountType == AccountType.AUTH_LIABILITY &&
                    (entries[0].journalEntry.postings[1] as Posting.Credit).amount.value == 10000L &&
                    (entries[0].journalEntry.postings[1] as Posting.Credit).amount.currency == "EUR" &&
                    
                    // 2. CAPTURE entry
                    entries[1].journalEntry.id == "CAPTURE:paymentorder-123" &&
                    entries[1].journalEntry.txType == JournalType.CAPTURE &&
                    entries[1].journalEntry.name == "Payment Capture" &&
                    entries[1].journalEntry.postings.size == 4 &&
                    (entries[1].journalEntry.postings[2] as Posting.Credit).account.accountType == AccountType.MERCHANT_ACCOUNT &&
                    (entries[1].journalEntry.postings[2] as Posting.Credit).account.accountId == "seller-789" &&
                    (entries[1].journalEntry.postings[2] as Posting.Credit).amount.value == 10000L &&
                    
                    // 3. SETTLEMENT entry
                    entries[2].journalEntry.id == "SETTLEMENT:paymentorder-123" &&
                    entries[2].journalEntry.txType == JournalType.SETTLEMENT &&
                    entries[2].journalEntry.name == "Funds received from Acquirer" &&
                    entries[2].journalEntry.postings.size == 4 &&
                    
                    // 4. FEE entry
                    entries[3].journalEntry.id == "PSP-FEE:paymentorder-123" &&
                    entries[3].journalEntry.txType == JournalType.FEE &&
                    entries[3].journalEntry.name == "Psp Fee is recorded" &&
                    entries[3].journalEntry.postings.size == 2 &&
                    (entries[3].journalEntry.postings[0] as Posting.Debit).account.accountType == AccountType.MERCHANT_ACCOUNT &&
                    (entries[3].journalEntry.postings[0] as Posting.Debit).account.accountId == "seller-789" &&
                    (entries[3].journalEntry.postings[0] as Posting.Debit).amount.value == 200L &&
                    (entries[3].journalEntry.postings[1] as Posting.Credit).account.accountType == AccountType.PROCESSING_FEE_REVENUE &&
                    (entries[3].journalEntry.postings[1] as Posting.Credit).amount.value == 200L &&
                    
                    // 5. PAYOUT entry
                    entries[4].journalEntry.id == "PAYOUT:paymentorder-123" &&
                    entries[4].journalEntry.txType == JournalType.PAYOUT &&
                    entries[4].journalEntry.name == "Merchant Payout" &&
                    entries[4].journalEntry.postings.size == 2 &&
                    (entries[4].journalEntry.postings[0] as Posting.Debit).account.accountType == AccountType.MERCHANT_ACCOUNT &&
                    (entries[4].journalEntry.postings[0] as Posting.Debit).account.accountId == "seller-789" &&
                    (entries[4].journalEntry.postings[0] as Posting.Debit).amount.value == 9800L &&
                    (entries[4].journalEntry.postings[1] as Posting.Credit).account.accountType == AccountType.ACQUIRER_ACCOUNT &&
                    (entries[4].journalEntry.postings[1] as Posting.Credit).account.accountId == "seller-789" &&
                    (entries[4].journalEntry.postings[1] as Posting.Credit).amount.value == 9800L &&
                    
                    // All entries have EUR currency
                    entries.all { entry -> entry.journalEntry.postings.all { it.amount.currency == "EUR" } }
                }
            )
        }

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
        
        every { ledgerWritePort.postLedgerEntriesAtomic(any()) } throws RuntimeException("Database write failed")

        // When/Then - Should propagate exception before publishing
        assertThrows<RuntimeException> {
            service.recordLedgerEntries(command)
        }

        // Verify that postLedgerEntriesAtomic was attempted
        verify(exactly = 1) { ledgerWritePort.postLedgerEntriesAtomic(any()) }
        
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
        val capturedEntries = slot<List<LedgerEntry>>()
        val capturedEvent = slot<LedgerEntriesRecorded>()
        
        every { ledgerWritePort.postLedgerEntriesAtomic(capture(capturedEntries)) } returns Unit
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

        // Verify that entries were persisted before exception
        assertTrue(capturedEntries.captured.isNotEmpty())
        assertEquals(5, capturedEntries.captured.size)
        
        // Verify that event data was being sent before exception
        assertNotNull(capturedEvent.captured)
        assertTrue(capturedEvent.captured.ledgerBatchId.startsWith("ledger-batch-"))
        assertEquals(command.publicPaymentOrderId, capturedEvent.captured.publicPaymentOrderId)
    }

    @Test
    fun `should verify explicit parameters for ledger entries recording`() {
        // Given - Verify all entries are persisted correctly
        val command = sampleCommand("SUCCESSFUL_FINAL")
        
        every { ledgerWritePort.postLedgerEntriesAtomic(any()) } returns Unit
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

        // Then - Verify batch was called with 5 entries
        verify(exactly = 1) {
            ledgerWritePort.postLedgerEntriesAtomic(
                match { entries -> entries.size == 5 }
            )
        }

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