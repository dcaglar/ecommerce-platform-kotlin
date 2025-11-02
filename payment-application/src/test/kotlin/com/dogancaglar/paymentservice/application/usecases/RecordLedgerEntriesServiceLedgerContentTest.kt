package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Posting
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import com.dogancaglar.paymentservice.util.LedgerEntriesRecordedTestHelper
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

        // Capture the ledger entries passed to postLedgerEntriesAtomic
        val capturedLedgerEntries = slot<List<LedgerEntry>>()
        
        // Setup mocks to accept calls - return LedgerEntry objects with populated IDs (simulating database)
        every { ledgerWritePort.postLedgerEntriesAtomic(capture(capturedLedgerEntries)) } answers {
            val entries = firstArg<List<LedgerEntry>>()
            // Populate IDs in the entries (in-place mutation, simulating adapter behavior)
            entries.forEachIndexed { index, entry ->
                entry.ledgerEntryId = if (index == 0) 1001L else 1002L
            }
            entries // Return entries with populated IDs
        }
        
        // Capture the event passed to publishSync
        val capturedEvent = slot<LedgerEntriesRecorded>()
        every { 
            eventPublisherPort.publishSync<LedgerEntriesRecorded>(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.sellerId,
                data = capture(capturedEvent),
                parentEventId = expectedEventId,
                traceId = expectedTraceId
            ) 
        } returns mockk()

        // when - service processes the command
        service.recordLedgerEntries(command)

        // ========== Then - Verify ledgerWritePort.postLedgerEntriesAtomic was called with exact ledger entries ==========
        verify(exactly = 1) { ledgerWritePort.postLedgerEntriesAtomic(any()) }
        
        val ledgerEntries = capturedLedgerEntries.captured
        assertEquals(2, ledgerEntries.size, "Should pass 2 ledger entries")
        
        // Verify First LedgerEntry (AUTH_HOLD)
        val authLedgerEntry = ledgerEntries[0]
        assertNotNull(authLedgerEntry.createdAt, "AUTH_HOLD ledger entry should have createdAt")
        assertEquals("AUTH:paymentorder-123", authLedgerEntry.journalEntry.id)
        assertEquals(JournalType.AUTH_HOLD, authLedgerEntry.journalEntry.txType)
        assertEquals("Authorization Hold", authLedgerEntry.journalEntry.name)
        assertEquals(2, authLedgerEntry.journalEntry.postings.size, "AUTH_HOLD should have 2 postings")
        
        // AUTH_HOLD Posting 1: AUTH_RECEIVABLE.GLOBAL DEBIT
        val authPosting1 = authLedgerEntry.journalEntry.postings[0] as Posting.Debit
        assertEquals("AUTH_RECEIVABLE.GLOBAL", authPosting1.account.accountCode)
        assertEquals(AccountType.AUTH_RECEIVABLE, authPosting1.account.type)
        assertEquals(10_000L, authPosting1.amount.quantity)
        assertEquals("EUR", authPosting1.amount.currency.currencyCode)
        
        // AUTH_HOLD Posting 2: AUTH_LIABILITY.GLOBAL CREDIT
        val authPosting2 = authLedgerEntry.journalEntry.postings[1] as Posting.Credit
        assertEquals("AUTH_LIABILITY.GLOBAL", authPosting2.account.accountCode)
        assertEquals(AccountType.AUTH_LIABILITY, authPosting2.account.type)
        assertEquals(10_000L, authPosting2.amount.quantity)
        assertEquals("EUR", authPosting2.amount.currency.currencyCode)
        
        // Verify Second LedgerEntry (CAPTURE)
        val captureLedgerEntry = ledgerEntries[1]
        assertNotNull(captureLedgerEntry.createdAt, "CAPTURE ledger entry should have createdAt")
        assertEquals("CAPTURE:paymentorder-123", captureLedgerEntry.journalEntry.id)
        assertEquals(JournalType.CAPTURE, captureLedgerEntry.journalEntry.txType)
        assertEquals("Payment Capture", captureLedgerEntry.journalEntry.name)
        assertEquals(4, captureLedgerEntry.journalEntry.postings.size, "CAPTURE should have 4 postings")
        
        // CAPTURE Posting 1: AUTH_RECEIVABLE.GLOBAL CREDIT
        val capturePosting1 = captureLedgerEntry.journalEntry.postings[0] as Posting.Credit
        assertEquals("AUTH_RECEIVABLE.GLOBAL", capturePosting1.account.accountCode)
        assertEquals(AccountType.AUTH_RECEIVABLE, capturePosting1.account.type)
        assertEquals(10_000L, capturePosting1.amount.quantity)
        assertEquals("EUR", capturePosting1.amount.currency.currencyCode)
        
        // CAPTURE Posting 2: AUTH_LIABILITY.GLOBAL DEBIT
        val capturePosting2 = captureLedgerEntry.journalEntry.postings[1] as Posting.Debit
        assertEquals("AUTH_LIABILITY.GLOBAL", capturePosting2.account.accountCode)
        assertEquals(AccountType.AUTH_LIABILITY, capturePosting2.account.type)
        assertEquals(10_000L, capturePosting2.amount.quantity)
        assertEquals("EUR", capturePosting2.amount.currency.currencyCode)
        
        // CAPTURE Posting 3: MERCHANT_ACCOUNT.seller-789 CREDIT
        val capturePosting3 = captureLedgerEntry.journalEntry.postings[2] as Posting.Credit
        assertEquals("MERCHANT_ACCOUNT.seller-789", capturePosting3.account.accountCode)
        assertEquals(AccountType.MERCHANT_ACCOUNT, capturePosting3.account.type)
        assertEquals("seller-789", capturePosting3.account.entityId)
        assertEquals(10_000L, capturePosting3.amount.quantity)
        assertEquals("EUR", capturePosting3.amount.currency.currencyCode)
        
        // CAPTURE Posting 4: PSP_RECEIVABLES.GLOBAL DEBIT
        val capturePosting4 = captureLedgerEntry.journalEntry.postings[3] as Posting.Debit
        assertEquals("PSP_RECEIVABLES.GLOBAL", capturePosting4.account.accountCode)
        assertEquals(AccountType.PSP_RECEIVABLES, capturePosting4.account.type)
        assertEquals(10_000L, capturePosting4.amount.quantity)
        assertEquals("EUR", capturePosting4.amount.currency.currencyCode)

        // ========== Then - Verify publishSync was called with exact LedgerEntriesRecorded ==========
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.sellerId,
                data = any(),
                parentEventId = expectedEventId,
                traceId = expectedTraceId
            )
        }
        
        val event = capturedEvent.captured
        assertNotNull(event, "Event should be captured")
        
        // Generate expected event using helper
        val expectedEvent = LedgerEntriesRecordedTestHelper.expectedAuthHoldAndCaptureEvent(
            paymentOrderId = command.paymentOrderId,
            publicPaymentOrderId = command.publicPaymentOrderId,
            sellerId = command.sellerId,
            amount = command.amountValue,
            currency = command.currency,
            authLedgerEntryId = 1001L,
            captureLedgerEntryId = 1002L,
            recordedAt = event.recordedAt, // Use actual recordedAt from captured event
            authCreatedAt = capturedLedgerEntries.captured[0].createdAt!!,
            captureCreatedAt = capturedLedgerEntries.captured[1].createdAt!!,
            status = command.status,
            traceId = expectedTraceId,
            parentEventId = expectedEventId.toString()
        )
        
        // Verify LedgerEntriesRecorded fields (batchId is generated, so only verify prefix)
        assertTrue(event.ledgerBatchId.startsWith("ledger-batch-"), "ledgerBatchId should start with 'ledger-batch-'")
        assertEquals(expectedEvent.entryCount, event.entryCount, "entryCount should be 2")
        assertEquals(expectedEvent.ledgerEntries.size, event.ledgerEntries.size, "ledgerEntries should contain 2 entries")
        assertEquals(expectedEvent.publicPaymentOrderId, event.publicPaymentOrderId)
        assertEquals(expectedEvent.paymentOrderId, event.paymentOrderId)
        assertEquals(expectedEvent.sellerId, event.sellerId)
        assertEquals(expectedEvent.currency, event.currency)
        assertEquals(expectedEvent.status, event.status)
        assertEquals(expectedEvent.traceId, event.traceId)
        assertEquals(expectedEvent.parentEventId, event.parentEventId)
        
        // ========== Verify First LedgerEntryEventData (AUTH_HOLD) ==========
        val authLedgerEntryEvent = event.ledgerEntries[0]
        val expectedAuthEntry = expectedEvent.ledgerEntries[0]
        assertEquals(expectedAuthEntry.ledgerEntryId, authLedgerEntryEvent.ledgerEntryId)
        assertEquals(expectedAuthEntry.journalEntryId, authLedgerEntryEvent.journalEntryId)
        assertEquals(expectedAuthEntry.journalType, authLedgerEntryEvent.journalType)
        assertEquals(expectedAuthEntry.journalName, authLedgerEntryEvent.journalName)
        assertNotNull(authLedgerEntryEvent.createdAt)
        assertEquals(expectedAuthEntry.postings.size, authLedgerEntryEvent.postings.size, "AUTH_HOLD should have 2 postings")
        
        // Verify all AUTH_HOLD postings
        expectedAuthEntry.postings.forEachIndexed { index, expectedPosting ->
            val actualPosting = authLedgerEntryEvent.postings[index]
            assertEquals(expectedPosting.accountCode, actualPosting.accountCode)
            assertEquals(expectedPosting.accountType, actualPosting.accountType)
            assertEquals(expectedPosting.amount, actualPosting.amount)
            assertEquals(expectedPosting.currency, actualPosting.currency)
            assertEquals(expectedPosting.direction, actualPosting.direction)
        }
        
        // ========== Verify Second LedgerEntryEventData (CAPTURE) ==========
        val captureLedgerEntryEvent = event.ledgerEntries[1]
        val expectedCaptureEntry = expectedEvent.ledgerEntries[1]
        assertEquals(expectedCaptureEntry.ledgerEntryId, captureLedgerEntryEvent.ledgerEntryId)
        assertEquals(expectedCaptureEntry.journalEntryId, captureLedgerEntryEvent.journalEntryId)
        assertEquals(expectedCaptureEntry.journalType, captureLedgerEntryEvent.journalType)
        assertEquals(expectedCaptureEntry.journalName, captureLedgerEntryEvent.journalName)
        assertNotNull(captureLedgerEntryEvent.createdAt)
        assertEquals(expectedCaptureEntry.postings.size, captureLedgerEntryEvent.postings.size, "CAPTURE should have 4 postings")
        
        // Verify all CAPTURE postings
        expectedCaptureEntry.postings.forEachIndexed { index, expectedPosting ->
            val actualPosting = captureLedgerEntryEvent.postings[index]
            assertEquals(expectedPosting.accountCode, actualPosting.accountCode)
            assertEquals(expectedPosting.accountType, actualPosting.accountType)
            assertEquals(expectedPosting.amount, actualPosting.amount)
            assertEquals(expectedPosting.currency, actualPosting.currency)
            assertEquals(expectedPosting.direction, actualPosting.direction)
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
        val expectedException = RuntimeException("Database write failed")
        
        // Capture what was attempted to be persisted
        val capturedLedgerEntries = slot<List<LedgerEntry>>()
        every { ledgerWritePort.postLedgerEntriesAtomic(capture(capturedLedgerEntries)) } throws expectedException

        // When/Then - Should propagate exception before publishing
        val actualException = assertThrows<RuntimeException> {
            service.recordLedgerEntries(command)
        }

        // Verify the exception message
        assertEquals("Database write failed", actualException.message)
        
        // Verify that postLedgerEntriesAtomic was attempted with correct data
        verify(exactly = 1) { ledgerWritePort.postLedgerEntriesAtomic(any()) }
        assertEquals(2, capturedLedgerEntries.captured.size, "Should attempt to persist 2 ledger entries")
        
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
    fun `should handle exception in publishSync and propagate exception after successful persistence`() {
        // Given
        val command = sampleCommand("SUCCESSFUL_FINAL")
        val expectedException = RuntimeException("Event publish error")
        val capturedEntries = slot<List<LedgerEntry>>()
        val capturedEvent = slot<LedgerEntriesRecorded>()
        
        // Setup: persistence succeeds, but publishing fails
        every { ledgerWritePort.postLedgerEntriesAtomic(capture(capturedEntries)) } answers {
            val entries = firstArg<List<LedgerEntry>>()
            // Populate IDs in the entries (in-place mutation, simulating adapter behavior)
            entries.forEachIndexed { index, entry ->
                entry.ledgerEntryId = if (index == 0) 1001L else 1002L
            }
            entries // Return entries with populated IDs
        }
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.sellerId,
                data = capture(capturedEvent),
                parentEventId = any(),
                traceId = any()
            )
        } throws expectedException

        // When/Then - Should propagate exception from publishSync
        val actualException = assertThrows<RuntimeException> {
            service.recordLedgerEntries(command)
        }

        // Verify the exception message
        assertEquals("Event publish error", actualException.message)

        // Verify that postLedgerEntriesAtomic was called and succeeded BEFORE the exception
        verify(exactly = 1) { ledgerWritePort.postLedgerEntriesAtomic(any()) }
        assertEquals(2, capturedEntries.captured.size, "Should persist 2 ledger entries")
        
        // Verify that entries have IDs populated (persistence succeeded)
        assertEquals(1001L, capturedEntries.captured[0].ledgerEntryId)
        assertEquals(1002L, capturedEntries.captured[1].ledgerEntryId)
        
        // Verify that event was constructed with correct data before exception
        assertNotNull(capturedEvent.captured)
        assertTrue(capturedEvent.captured.ledgerBatchId.startsWith("ledger-batch-"))
        assertEquals(command.publicPaymentOrderId, capturedEvent.captured.publicPaymentOrderId)
        assertEquals(command.sellerId, capturedEvent.captured.sellerId)
        assertEquals(2, capturedEvent.captured.entryCount)
        assertEquals(2, capturedEvent.captured.ledgerEntries.size)
        
        // Verify publishSync was called (and then threw exception)
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.sellerId,
                data = any(),
                parentEventId = any(),
                traceId = any()
            )
        }
    }

}