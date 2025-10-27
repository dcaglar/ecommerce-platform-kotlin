package com.dogancaglar.paymentservice.application.usecases

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
        // given
        val command = sampleCommand("SUCCESSFUL_FINAL")
        val captured = mutableListOf<LedgerEntry>()
        val capturedEvent = slot<LedgerEntriesRecorded>()

        every { ledgerWritePort.appendLedgerEntry(capture(captured)) } returns Unit
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.publicPaymentOrderId,
                data = capture(capturedEvent),
                parentEventId = any(),
                traceId = any()
            )
        } returns mockk()

        // when
        service.recordLedgerEntries(command)

        // then
        val expectedEntries = JournalEntryFactory.fullFlow(
            command.publicPaymentOrderId,
            Amount(command.amountValue, command.currency),
            Account(command.sellerId, AccountType.MERCHANT_ACCOUNT),
            Account(command.sellerId, AccountType.ACQUIRER_ACCOUNT)
        )

        // ✅ number of persisted entries = number of factory entries
        assertEquals(expectedEntries.size, captured.size)

        // ✅ compare txTypes and posting structures one by one
        expectedEntries.zip(captured).forEachIndexed { index, (expected, persisted) ->
            // Verify metadata
            assertEquals(0L, persisted.ledgerEntryId, "ledgerEntryId should be unassigned (0) before DB insert at entry #$index")
            assertNotNull(persisted.createdAt, "createdAt should be set at entry #$index")
            
            // Verify business content
            assertEquals(expected.txType, persisted.journalEntry.txType, "Mismatch at entry #$index")
            assertEquals(expected.id, persisted.journalEntry.id, "JournalEntry ID mismatch at entry #$index")

            // each posting should match in accountType, amount, and debit/credit class
            expected.postings.zip(persisted.journalEntry.postings).forEachIndexed { i, (expPost, gotPost) ->
                assertEquals(expPost::class, gotPost::class, "Posting[$i] type mismatch at entry #$index")
                assertEquals(expPost.account.accountType, gotPost.account.accountType, "AccountType mismatch at entry #$index[$i]")
                assertEquals(expPost.amount.value, gotPost.amount.value, "Amount mismatch at entry #$index[$i]")
                assertEquals(expPost.amount.currency, gotPost.amount.currency, "Currency mismatch at entry #$index[$i]")
            }
        }

        // ✅ ensure event published once with correct metadata and explicit parameters
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.publicPaymentOrderId,
                data = match { it is LedgerEntriesRecorded },
                parentEventId = any(),
                traceId = any()
            )
        }
        
        // ✅ Verify captured event data
        val event = capturedEvent.captured
        assertNotNull(event)
        assertTrue(event.ledgerBatchId.startsWith("ledger-batch-"))
        assertEquals(expectedEntries.size, event.entryCount)
        assertEquals(command.publicPaymentOrderId, event.publicPaymentOrderId)
        assertEquals(command.paymentOrderId, event.paymentOrderId)
        assertEquals(command.sellerId, event.sellerId)
        assertEquals(command.currency, event.currency)
        assertEquals(command.status, event.status)
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
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.publicPaymentOrderId,
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

    @Test
    fun `should verify explicit parameters for ledger entries recording`() {
        // Given - Verify all entries are persisted correctly
        val command = sampleCommand("SUCCESSFUL_FINAL")
        val capturedEntries = mutableListOf<LedgerEntry>()
        
        every { ledgerWritePort.appendLedgerEntry(capture(capturedEntries)) } returns Unit
        // Service uses default parameter values (preSetEventIdFromCaller, timeoutSeconds)
        every {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.publicPaymentOrderId,
                data = any(),
                parentEventId = any(),
                traceId = any()
            )
        } returns mockk()

        // When
        service.recordLedgerEntries(command)

        // Then - Verify it was called
        verify(exactly = 1) {
            eventPublisherPort.publishSync(
                eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
                aggregateId = command.publicPaymentOrderId,
                data = match { it is LedgerEntriesRecorded },
                parentEventId = any(),
                traceId = any()
            )
        }
        
        // Verify entries were persisted correctly
        assertEquals(5, capturedEntries.size) // fullFlow creates 5 entries
    }
}