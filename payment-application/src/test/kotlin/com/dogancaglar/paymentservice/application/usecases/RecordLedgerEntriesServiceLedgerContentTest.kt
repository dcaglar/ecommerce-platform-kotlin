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
        
        // 1. Verify AUTH_HOLD entry
        verify(exactly = 1) {
            ledgerWritePort.appendLedgerEntry(
                match { entry ->
                    entry.ledgerEntryId == 0L &&
                    entry.createdAt != null &&
                    entry.journalEntry.id == "AUTH:paymentorder-123" &&
                    entry.journalEntry.txType == JournalType.AUTH_HOLD &&
                    entry.journalEntry.name == "Authorization Hold" &&
                    entry.journalEntry.postings.size == 2 &&
                    // Posting 1: DEBIT AUTH_RECEIVABLE
                    (entry.journalEntry.postings[0] as Posting.Debit).account.accountType == AccountType.AUTH_RECEIVABLE &&
                    (entry.journalEntry.postings[0] as Posting.Debit).amount.value == 10000L &&
                    (entry.journalEntry.postings[0] as Posting.Debit).amount.currency == "EUR" &&
                    // Posting 2: CREDIT AUTH_LIABILITY
                    (entry.journalEntry.postings[1] as Posting.Credit).account.accountType == AccountType.AUTH_LIABILITY &&
                    (entry.journalEntry.postings[1] as Posting.Credit).amount.value == 10000L &&
                    (entry.journalEntry.postings[1] as Posting.Credit).amount.currency == "EUR"
                }
            )
        }
        
        // 2. Verify CAPTURE entry
        verify(exactly = 1) {
            ledgerWritePort.appendLedgerEntry(
                match { entry ->
                    entry.ledgerEntryId == 0L &&
                    entry.createdAt != null &&
                    entry.journalEntry.id == "CAPTURE:paymentorder-123" &&
                    entry.journalEntry.txType == JournalType.CAPTURE &&
                    entry.journalEntry.name == "Payment Capture" &&
                    entry.journalEntry.postings.size == 4 &&
                    // Posting 1: CREDIT AUTH_RECEIVABLE
                    (entry.journalEntry.postings[0] as Posting.Credit).account.accountType == AccountType.AUTH_RECEIVABLE &&
                    (entry.journalEntry.postings[0] as Posting.Credit).amount.value == 10000L &&
                    // Posting 2: DEBIT AUTH_LIABILITY
                    (entry.journalEntry.postings[1] as Posting.Debit).account.accountType == AccountType.AUTH_LIABILITY &&
                    (entry.journalEntry.postings[1] as Posting.Debit).amount.value == 10000L &&
                    // Posting 3: CREDIT MERCHANT_ACCOUNT
                    (entry.journalEntry.postings[2] as Posting.Credit).account.accountType == AccountType.MERCHANT_ACCOUNT &&
                    (entry.journalEntry.postings[2] as Posting.Credit).account.accountId == "seller-789" &&
                    (entry.journalEntry.postings[2] as Posting.Credit).amount.value == 10000L &&
                    // Posting 4: DEBIT PSP_RECEIVABLES
                    (entry.journalEntry.postings[3] as Posting.Debit).account.accountType == AccountType.PSP_RECEIVABLES &&
                    (entry.journalEntry.postings[3] as Posting.Debit).amount.value == 10000L &&
                    entry.journalEntry.postings.all { it.amount.currency == "EUR" }
                }
            )
        }
        
        // 3. Verify SETTLEMENT entry
        verify(exactly = 1) {
            ledgerWritePort.appendLedgerEntry(
                match { entry ->
                    entry.ledgerEntryId == 0L &&
                    entry.createdAt != null &&
                    entry.journalEntry.id == "SETTLEMENT:paymentorder-123" &&
                    entry.journalEntry.txType == JournalType.SETTLEMENT &&
                    entry.journalEntry.name == "Funds received from Acquirer" &&
                    entry.journalEntry.postings.size == 4 &&
                    // Posting 1: DEBIT SCHEME_FEES (0)
                    (entry.journalEntry.postings[0] as Posting.Debit).account.accountType == AccountType.SCHEME_FEES &&
                    (entry.journalEntry.postings[0] as Posting.Debit).amount.value == 0L &&
                    // Posting 2: DEBIT INTERCHANGE_FEES (0)
                    (entry.journalEntry.postings[1] as Posting.Debit).account.accountType == AccountType.INTERCHANGE_FEES &&
                    (entry.journalEntry.postings[1] as Posting.Debit).amount.value == 0L &&
                    // Posting 3: DEBIT ACQUIRER_ACCOUNT (10000-0-0=10000)
                    (entry.journalEntry.postings[2] as Posting.Debit).account.accountType == AccountType.ACQUIRER_ACCOUNT &&
                    (entry.journalEntry.postings[2] as Posting.Debit).account.accountId == "seller-789" &&
                    (entry.journalEntry.postings[2] as Posting.Debit).amount.value == 10000L &&
                    // Posting 4: CREDIT PSP_RECEIVABLES (10000)
                    (entry.journalEntry.postings[3] as Posting.Credit).account.accountType == AccountType.PSP_RECEIVABLES &&
                    (entry.journalEntry.postings[3] as Posting.Credit).amount.value == 10000L &&
                    entry.journalEntry.postings.all { it.amount.currency == "EUR" }
                }
            )
        }
        
        // 4. Verify FEE entry
        verify(exactly = 1) {
            ledgerWritePort.appendLedgerEntry(
                match { entry ->
                    entry.ledgerEntryId == 0L &&
                    entry.createdAt != null &&
                    entry.journalEntry.id == "PSP-FEE:paymentorder-123" &&
                    entry.journalEntry.txType == JournalType.FEE &&
                    entry.journalEntry.name == "Psp Fee is recorded" &&
                    entry.journalEntry.postings.size == 2 &&
                    // Posting 1: DEBIT MERCHANT_ACCOUNT (200 fee)
                    (entry.journalEntry.postings[0] as Posting.Debit).account.accountType == AccountType.MERCHANT_ACCOUNT &&
                    (entry.journalEntry.postings[0] as Posting.Debit).account.accountId == "seller-789" &&
                    (entry.journalEntry.postings[0] as Posting.Debit).amount.value == 200L &&
                    // Posting 2: CREDIT PROCESSING_FEE_REVENUE (200 fee)
                    (entry.journalEntry.postings[1] as Posting.Credit).account.accountType == AccountType.PROCESSING_FEE_REVENUE &&
                    (entry.journalEntry.postings[1] as Posting.Credit).amount.value == 200L &&
                    entry.journalEntry.postings.all { it.amount.currency == "EUR" }
                }
            )
        }
        
        // 5. Verify PAYOUT entry
        verify(exactly = 1) {
            ledgerWritePort.appendLedgerEntry(
                match { entry ->
                    entry.ledgerEntryId == 0L &&
                    entry.createdAt != null &&
                    entry.journalEntry.id == "PAYOUT:paymentorder-123" &&
                    entry.journalEntry.txType == JournalType.PAYOUT &&
                    entry.journalEntry.name == "Merchant Payout" &&
                    entry.journalEntry.postings.size == 2 &&
                    // Posting 1: DEBIT MERCHANT_ACCOUNT (10000-200=9800)
                    (entry.journalEntry.postings[0] as Posting.Debit).account.accountType == AccountType.MERCHANT_ACCOUNT &&
                    (entry.journalEntry.postings[0] as Posting.Debit).account.accountId == "seller-789" &&
                    (entry.journalEntry.postings[0] as Posting.Debit).amount.value == 9800L &&
                    // Posting 2: CREDIT ACQUIRER_ACCOUNT (9800)
                    (entry.journalEntry.postings[1] as Posting.Credit).account.accountType == AccountType.ACQUIRER_ACCOUNT &&
                    (entry.journalEntry.postings[1] as Posting.Credit).account.accountId == "seller-789" &&
                    (entry.journalEntry.postings[1] as Posting.Credit).amount.value == 9800L &&
                    entry.journalEntry.postings.all { it.amount.currency == "EUR" }
                }
            )
        }

        // then - verify publishSync called with exact parameters
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.publicPaymentOrderId,
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
    fun `should handle exception in appendLedgerEntry and not publish event`() {
        // Given
        val command = sampleCommand("SUCCESSFUL_FINAL")
        
        every { ledgerWritePort.appendLedgerEntry(any()) } throws RuntimeException("Database write failed")

        // When/Then - Should propagate exception before publishing
        assertThrows<RuntimeException> {
            service.recordLedgerEntries(command)
        }

        // Verify that appendLedgerEntry was attempted
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

        // Verify that entries were persisted before exception
        assertTrue(capturedEntries.isNotEmpty())
        
        // Verify that event data was being sent before exception
        assertNotNull(capturedEvent.captured)
        assertTrue(capturedEvent.captured.ledgerBatchId.startsWith("ledger-batch-"))
        assertEquals(command.publicPaymentOrderId, capturedEvent.captured.publicPaymentOrderId)
    }

}