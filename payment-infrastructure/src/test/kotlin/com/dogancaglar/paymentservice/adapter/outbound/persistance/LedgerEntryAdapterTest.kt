package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.LedgerMapper
import com.dogancaglar.paymentservice.application.model.LedgerEntry
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Unit tests for LedgerEntryAdapter.
 * 
 * Tests the exact behavior of appendLedgerEntry with explicit verification:
 * 1. Successful insert: maps and persists journal entry + all postings
 * 2. Duplicate journal entry: skips posting inserts when insertJournalEntry returns 0
 * 3. Exception handling: no posting inserts when insertJournalEntry throws
 */
class LedgerEntryAdapterTest {

    private lateinit var ledgerMapper: LedgerMapper
    private lateinit var adapter: LedgerEntryAdapter

    @BeforeEach
    fun setUp() {
        ledgerMapper = mockk(relaxed = true)
        adapter = LedgerEntryAdapter(ledgerMapper)
    }

    // ==================== Test 1: Successful Insert ====================

    @Test
    fun `appendLedgerEntry should map and persist journal entry and all postings when successful`() {
        // Given - AUTH_HOLD creates 2 postings
        val amount = Amount(10000L, "USD")
        val journalEntry = JournalEntry.authHold("PAY-123", amount)
        val ledgerEntry = LedgerEntry.create(0L, journalEntry, LocalDateTime.now())
        
        // Setup: mapper returns 1 for any insert calls
        every { ledgerMapper.insertJournalEntry(any()) } returns 1
        every { ledgerMapper.insertPosting(any()) } returns 1

        // When
        adapter.appendLedgerEntry(ledgerEntry)

        // Then - verify journal entry inserted with exact attributes
        verify(exactly = 1) { 
            ledgerMapper.insertJournalEntry(
                match { entity ->
                    entity.id == "AUTH:PAY-123" &&
                    entity.txType == "AUTH_HOLD" &&
                    entity.name == "Authorization Hold"
                }
            ) 
        }
        
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

    // ==================== Test 2: Duplicate Journal Entry ====================

    @Test
    fun `appendLedgerEntry should skip postings when insertJournalEntry returns 0`() {
        // Given - CAPTURE creates 4 postings
        val amount = Amount(5000L, "EUR")
        val merchantAccount = Account("merchant-456", AccountType.MERCHANT_ACCOUNT)
        val journalEntry = JournalEntry.capture("PAY-789", amount, merchantAccount)
        val ledgerEntry = LedgerEntry.create(0L, journalEntry, LocalDateTime.now())
        
        // Setup: mapper returns 0 indicating duplicate
        every { ledgerMapper.insertJournalEntry(any()) } returns 0

        // When
        adapter.appendLedgerEntry(ledgerEntry)

        // Then - verify journal entry insert was attempted
        verify(exactly = 1) { 
            ledgerMapper.insertJournalEntry(
                match { entity ->
                    entity.id == "CAPTURE:PAY-789" &&
                    entity.txType == "CAPTURE"
                }
            ) 
        }
        
        // Then - verify NO postings were inserted (duplicate detected)
        verify(exactly = 0) { 
            ledgerMapper.insertPosting(
                match { posting ->
                    posting.journalId == "CAPTURE:PAY-789"
                }
            )
        }
    }

    // ==================== Test 3: Exception Handling ====================

    @Test
    fun `appendLedgerEntry should not insert postings when insertJournalEntry throws exception`() {
        // Given - AUTH_HOLD creates 2 postings
        val amount = Amount(10000L, "USD")
        val journalEntry = JournalEntry.authHold("PAY-999", amount)
        val ledgerEntry = LedgerEntry.create(0L, journalEntry, LocalDateTime.now())
        
        // Setup: mapper throws exception
        every { ledgerMapper.insertJournalEntry(any()) } throws RuntimeException("Database connection failed")

        // When/Then - verify exception is thrown
        org.junit.jupiter.api.assertThrows<RuntimeException> {
            adapter.appendLedgerEntry(ledgerEntry)
        }

        // Then - verify journal entry insert was attempted
        verify(exactly = 1) { 
            ledgerMapper.insertJournalEntry(
                match { entity ->
                    entity.id == "AUTH:PAY-999"
                }
            )
        }
        
        // Then - verify NO postings were inserted
        verify(exactly = 0) { 
            ledgerMapper.insertPosting(
                match { posting ->
                    posting.journalId == "AUTH:PAY-999"
                }
            )
        }
    }
}
