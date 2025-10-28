package com.dogancaglar.paymentservice.service

import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.LedgerMapper
import com.dogancaglar.paymentservice.application.usecases.LedgerEntryFactory
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for LedgerEntryTxAdapter using MockK.
 * 
 * Tests verify:
 * - Batch persistence of multiple ledger entries
 * - Duplicate journal entry handling (skips posting inserts)
 * - Mapping from LedgerEntry to entities
 * - Exception handling at different stages
 * - Empty list handling
 */
class LedgerEntryTxAdapterTest {

    private lateinit var ledgerMapper: LedgerMapper
    private lateinit var adapter: LedgerEntryTxAdapter
    private lateinit var clock: Clock
    private lateinit var factory: LedgerEntryFactory

    @BeforeEach
    fun setUp() {
        ledgerMapper = mockk(relaxed = true)
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)
        factory = LedgerEntryFactory(clock)
        adapter = LedgerEntryTxAdapter(ledgerMapper)
    }

    // ==================== Successful Batch Persistence ====================

    @Test
    fun `postLedgerEntriesAtomic should persist all entries and postings when successful`() {
        // Given - two journal entries with different postings
        val amount1 = Amount(10000L, "USD")
        val journal1 = JournalEntry.authHold("PAY-123", amount1)
        val entry1 = factory.create(journal1)

        val amount2 = Amount(5000L, "EUR")
        val merchantAccount = Account("merchant-456", AccountType.MERCHANT_ACCOUNT)
        val journal2 = JournalEntry.capture("PAY-456", amount2, merchantAccount)
        val entry2 = factory.create(journal2)

        val entries = listOf(entry1, entry2)

        // Setup: mapper returns 1 for all inserts
        every { ledgerMapper.insertJournalEntry(any()) } returns 1
        every { ledgerMapper.insertPosting(any()) } returns 1

        // When
        adapter.postLedgerEntriesAtomic(entries)

        // Then - verify journal entry 1 (AUTH_HOLD with 2 postings)
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity ->
                    entity.id == "AUTH:PAY-123" &&
                    entity.txType == "AUTH_HOLD" &&
                    entity.name == "Authorization Hold"
                }
            )
        }
        verify(exactly = 2) {
            ledgerMapper.insertPosting(
                match { posting ->
                    posting.journalId == "AUTH:PAY-123"
                }
            )
        }

        // Then - verify journal entry 2 (CAPTURE with 4 postings)
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity ->
                    entity.id == "CAPTURE:PAY-456" &&
                    entity.txType == "CAPTURE" &&
                    entity.name == "Payment Capture"
                }
            )
        }
        verify(exactly = 4) {
            ledgerMapper.insertPosting(
                match { posting ->
                    posting.journalId == "CAPTURE:PAY-456"
                }
            )
        }
    }

    @Test
    fun `postLedgerEntriesAtomic should verify exact posting details for AUTH_HOLD`() {
        // Given
        val amount = Amount(10000L, "USD")
        val journal = JournalEntry.authHold("PAY-123", amount)
        val entry = factory.create(journal)

        every { ledgerMapper.insertJournalEntry(any()) } returns 1
        every { ledgerMapper.insertPosting(any()) } returns 1

        // When
        adapter.postLedgerEntriesAtomic(listOf(entry))

        // Then - verify posting 1: AUTH_RECEIVABLE DEBIT
        verify(exactly = 1) {
            ledgerMapper.insertPosting(
                match { posting ->
                    posting.journalId == "AUTH:PAY-123" &&
                    posting.accountCode == "PSP.AUTH_RECEIVABLE" &&
                    posting.accountType == "AUTH_RECEIVABLE" &&
                    posting.direction == "DEBIT" &&
                    posting.amount == 10000L &&
                    posting.currency == "USD"
                }
            )
        }

        // Then - verify posting 2: AUTH_LIABILITY CREDIT
        verify(exactly = 1) {
            ledgerMapper.insertPosting(
                match { posting ->
                    posting.journalId == "AUTH:PAY-123" &&
                    posting.accountCode == "PSP.AUTH_LIABILITY" &&
                    posting.accountType == "AUTH_LIABILITY" &&
                    posting.direction == "CREDIT" &&
                    posting.amount == 10000L &&
                    posting.currency == "USD"
                }
            )
        }
    }

    @Test
    fun `postLedgerEntriesAtomic should verify exact posting details for CAPTURE`() {
        // Given
        val amount = Amount(5000L, "EUR")
        val merchantAccount = Account("merchant-456", AccountType.MERCHANT_ACCOUNT)
        val journal = JournalEntry.capture("PAY-789", amount, merchantAccount)
        val entry = factory.create(journal)

        every { ledgerMapper.insertJournalEntry(any()) } returns 1
        every { ledgerMapper.insertPosting(any()) } returns 1

        // When
        adapter.postLedgerEntriesAtomic(listOf(entry))

        // Then - verify all 4 postings
        verify(exactly = 4) {
            ledgerMapper.insertPosting(
                match { posting ->
                    posting.journalId == "CAPTURE:PAY-789" &&
                    posting.currency == "EUR" &&
                    posting.amount == 5000L
                }
            )
        }

        // Verify specific account types are present
        verify(atLeast = 1) {
            ledgerMapper.insertPosting(
                match { posting ->
                    posting.accountType == "AUTH_RECEIVABLE" &&
                    posting.direction == "CREDIT"
                }
            )
        }
        verify(atLeast = 1) {
            ledgerMapper.insertPosting(
                match { posting ->
                    posting.accountType == "MERCHANT_ACCOUNT" &&
                    posting.direction == "CREDIT" &&
                    posting.accountCode.contains("merchant-456")
                }
            )
        }
    }

    // ==================== Duplicate Handling ====================

    @Test
    fun `postLedgerEntriesAtomic should skip postings when insertJournalEntry returns 0 for duplicate`() {
        // Given
        val amount = Amount(10000L, "USD")
        val journal = JournalEntry.authHold("PAY-999", amount)
        val entry = factory.create(journal)

        // Setup: mapper returns 0 indicating duplicate
        every { ledgerMapper.insertJournalEntry(any()) } returns 0

        // When
        adapter.postLedgerEntriesAtomic(listOf(entry))

        // Then - verify journal entry insert was attempted
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity ->
                    entity.id == "AUTH:PAY-999"
                }
            )
        }

        // Then - verify NO postings were inserted (duplicate detected)
        verify(exactly = 0) {
            ledgerMapper.insertPosting(any())
        }
    }

    @Test
    fun `postLedgerEntriesAtomic should stop processing when duplicate is found`() {
        // Given - first entry is duplicate, second should be skipped entirely
        val amount1 = Amount(10000L, "USD")
        val journal1 = JournalEntry.authHold("PAY-111", amount1)
        val entry1 = factory.create(journal1)

        val amount2 = Amount(5000L, "EUR")
        val journal2 = JournalEntry.authHold("PAY-222", amount2)
        val entry2 = factory.create(journal2)

        val entries = listOf(entry1, entry2)

        // Setup: first returns 0 (duplicate), second should never be reached
        every { 
            ledgerMapper.insertJournalEntry(
                match { it.id == "AUTH:PAY-111" }
            ) 
        } returns 0

        // When
        adapter.postLedgerEntriesAtomic(entries)

        // Then - verify only first journal entry was attempted
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity -> entity.id == "AUTH:PAY-111" }
            )
        }

        // Then - verify second entry was never processed (return stops entire batch)
        verify(exactly = 0) {
            ledgerMapper.insertJournalEntry(
                match { entity -> entity.id == "AUTH:PAY-222" }
            )
        }

        // Then - verify NO postings were inserted (duplicate found, batch stopped)
        verify(exactly = 0) {
            ledgerMapper.insertPosting(any())
        }
    }

    // ==================== Exception Handling ====================

    @Test
    fun `postLedgerEntriesAtomic should propagate exception when insertJournalEntry throws`() {
        // Given
        val amount = Amount(10000L, "USD")
        val journal = JournalEntry.authHold("PAY-999", amount)
        val entry = factory.create(journal)

        // Setup: mapper throws exception
        every { ledgerMapper.insertJournalEntry(any()) } throws RuntimeException("Database connection failed")

        // When/Then
        assertThrows<RuntimeException> {
            adapter.postLedgerEntriesAtomic(listOf(entry))
        }

        // Then - verify journal entry insert was attempted
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(any())
        }

        // Then - verify NO postings were inserted
        verify(exactly = 0) {
            ledgerMapper.insertPosting(any())
        }
    }

    @Test
    fun `postLedgerEntriesAtomic should propagate exception when insertPosting throws`() {
        // Given
        val amount = Amount(10000L, "USD")
        val journal = JournalEntry.authHold("PAY-999", amount)
        val entry = factory.create(journal)

        // Setup: journal insert succeeds, posting insert fails
        every { ledgerMapper.insertJournalEntry(any()) } returns 1
        every { ledgerMapper.insertPosting(any()) } throws RuntimeException("Posting insert failed")

        // When/Then
        assertThrows<RuntimeException> {
            adapter.postLedgerEntriesAtomic(listOf(entry))
        }

        // Then - verify journal entry was inserted
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(any())
        }

        // Then - verify posting insert was attempted (and failed)
        verify(atLeast = 1) {
            ledgerMapper.insertPosting(any())
        }
    }

    @Test
    fun `postLedgerEntriesAtomic should handle exception and stop processing`() {
        // Given - three entries, second one will fail with exception
        val amount = Amount(10000L, "USD")
        val journal1 = JournalEntry.authHold("PAY-111", amount)
        val entry1 = factory.create(journal1)

        val journal2 = JournalEntry.authHold("PAY-222", amount)
        val entry2 = factory.create(journal2)

        val journal3 = JournalEntry.authHold("PAY-333", amount)
        val entry3 = factory.create(journal3)

        val entries = listOf(entry1, entry2, entry3)

        // Setup: first succeeds, second throws exception, third never reached
        every { 
            ledgerMapper.insertJournalEntry(
                match { it.id == "AUTH:PAY-111" }
            ) 
        } returns 1
        every { 
            ledgerMapper.insertJournalEntry(
                match { it.id == "AUTH:PAY-222" }
            ) 
        } throws RuntimeException("Second entry failed")
        every { ledgerMapper.insertPosting(any()) } returns 1

        // When/Then
        assertThrows<RuntimeException> {
            adapter.postLedgerEntriesAtomic(entries)
        }

        // Then - verify first entry was processed completely
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity -> entity.id == "AUTH:PAY-111" }
            )
        }
        verify(exactly = 2) {
            ledgerMapper.insertPosting(
                match { posting -> posting.journalId == "AUTH:PAY-111" }
            )
        }

        // Then - verify second entry was attempted but failed
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity -> entity.id == "AUTH:PAY-222" }
            )
        }

        // Then - verify third entry was never reached (exception stopped processing)
        verify(exactly = 0) {
            ledgerMapper.insertJournalEntry(
                match { entity -> entity.id == "AUTH:PAY-333" }
            )
        }
    }

    // ==================== Edge Cases ====================

    @Test
    fun `postLedgerEntriesAtomic should return early when entries list is empty`() {
        // When
        adapter.postLedgerEntriesAtomic(emptyList())

        // Then - verify no mapper calls were made
        verify(exactly = 0) { ledgerMapper.insertJournalEntry(any()) }
        verify(exactly = 0) { ledgerMapper.insertPosting(any()) }
    }

    @Test
    fun `postLedgerEntriesAtomic should handle single entry batch`() {
        // Given
        val amount = Amount(10000L, "USD")
        val journal = JournalEntry.authHold("PAY-123", amount)
        val entry = factory.create(journal)

        every { ledgerMapper.insertJournalEntry(any()) } returns 1
        every { ledgerMapper.insertPosting(any()) } returns 1

        // When
        adapter.postLedgerEntriesAtomic(listOf(entry))

        // Then
        verify(exactly = 1) { ledgerMapper.insertJournalEntry(any()) }
        verify(exactly = 2) { ledgerMapper.insertPosting(any()) }
    }

    @Test
    fun `postLedgerEntriesAtomic should handle fullFlow batch with 5 entries`() {
        // Given - create all 5 entries from a successful payment
        val amount = Amount(10000L, "EUR")
        val merchantAccount = Account("merchant-123", AccountType.MERCHANT_ACCOUNT)
        val acquirerAccount = Account("merchant-123", AccountType.ACQUIRER_ACCOUNT)

        val entries = JournalEntry.fullFlow("PAY-999", amount, merchantAccount, acquirerAccount)
            .map { factory.create(it) }

        every { ledgerMapper.insertJournalEntry(any()) } returns 1
        every { ledgerMapper.insertPosting(any()) } returns 1

        // When
        adapter.postLedgerEntriesAtomic(entries)

        // Then - verify all 5 journal entries were inserted
        verify(exactly = 5) { ledgerMapper.insertJournalEntry(any()) }

        // Then - verify total postings (2+4+4+2+2 = 14 postings)
        verify(exactly = 14) { ledgerMapper.insertPosting(any()) }

        // Verify each specific entry type
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity -> entity.txType == "AUTH_HOLD" }
            )
        }
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity -> entity.txType == "CAPTURE" }
            )
        }
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity -> entity.txType == "SETTLEMENT" }
            )
        }
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity -> entity.txType == "FEE" }
            )
        }
        verify(exactly = 1) {
            ledgerMapper.insertJournalEntry(
                match { entity -> entity.txType == "PAYOUT" }
            )
        }
    }
}

