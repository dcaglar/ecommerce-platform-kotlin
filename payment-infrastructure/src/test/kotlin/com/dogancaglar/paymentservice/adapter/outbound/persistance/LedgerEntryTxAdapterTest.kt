package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.LedgerMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry
import com.dogancaglar.paymentservice.domain.util.LedgerEntryFactory
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock

/**
 * Unit tests for LedgerEntryTxAdapter.
 * 
 * Tests the exact behavior of postLedgerEntriesAtomic with explicit verification:
 * 1. Successful insert: maps and persists journal entry + all postings
 * 2. Duplicate journal entry: skips posting inserts when insertJournalEntry returns 0
 * 3. Exception handling: no posting inserts when insertJournalEntry throws
 */
class LedgerEntryTxAdapterTest {

    private lateinit var ledgerMapper: LedgerMapper
    private lateinit var adapter: LedgerEntryTxAdapter
    private lateinit var ledgerEntryFactory: LedgerEntryFactory
    private val clock = Clock.systemUTC()

    @BeforeEach
    fun setUp() {
        ledgerMapper = mockk(relaxed = true)
        adapter = LedgerEntryTxAdapter(ledgerMapper)
        ledgerEntryFactory = LedgerEntryFactory(clock)
    }

    // ==================== Test 1: Comprehensive authHoldAndCapture Test ====================

    @Test
    fun `postLedgerEntriesAtomic should persist all ledger entries, journal entries and postings from authHoldAndCapture`() {
        // Given - AUTH_HOLD_AND_CAPTURE creates 2 journal entries with 6 postings total
        val usdCurrency = Currency("USD")
        val amount = Amount.of(10000L, usdCurrency)
        val merchantId = "merchant-123"
        val paymentOrderId = "PAY-123"
        val merchantAccount = Account.mock(AccountType.MERCHANT_ACCOUNT, merchantId, "USD")
        val authReceivable = Account.mock(AccountType.AUTH_RECEIVABLE, "GLOBAL", "USD")
        val authLiability = Account.mock(AccountType.AUTH_LIABILITY, "GLOBAL", "USD")
        val pspReceivable = Account.mock(AccountType.PSP_RECEIVABLES, "GLOBAL", "USD")
        val journalEntryList = JournalEntry.authHoldAndCapture(
            paymentOrderId, 
            amount,
            authReceivable,
            authLiability,
            merchantAccount,
            pspReceivable
        )
        
        assertEquals(2, journalEntryList.size, "authHoldAndCapture should create 2 journal entries")
        
        // Create ledger entries for both journal entries
        val authLedgerEntry = ledgerEntryFactory.create(journalEntryList[0]) // AUTH_HOLD
        val captureLedgerEntry = ledgerEntryFactory.create(journalEntryList[1]) // CAPTURE
        val ledgerEntries = listOf(authLedgerEntry, captureLedgerEntry)
        
        // Setup: mapper returns 1 for any insert calls, and populates ledger entry IDs
        val capturedLedgerEntities = mutableListOf<com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.LedgerEntryEntity>()
        val capturedJournalEntries = mutableListOf<com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.JournalEntryEntity>()
        val capturedPostings = mutableListOf<com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PostingEntity>()
        
        every { ledgerMapper.insertJournalEntry(any()) } answers {
            val entity = firstArg<com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.JournalEntryEntity>()
            capturedJournalEntries.add(entity)
            1
        }
        every { ledgerMapper.insertPosting(any()) } answers {
            val posting = firstArg<com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PostingEntity>()
            capturedPostings.add(posting)
            1
        }
        
        var ledgerEntryIdCounter = 1001L
        every { ledgerMapper.insertLedgerEntry(any()) } answers {
            val entity = firstArg<com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.LedgerEntryEntity>()
            entity.id = ledgerEntryIdCounter++
            capturedLedgerEntities.add(entity)
            1
        }

        // When
        val result = adapter.postLedgerEntriesAtomic(ledgerEntries)
        
        // ========== Then - Verify Returned Ledger Entries ==========
        assertEquals(2, result.size, "Should return 2 ledger entries")
        assertEquals(1001L, result[0].ledgerEntryId, "First LedgerEntry ID should be populated")
        assertEquals(1002L, result[1].ledgerEntryId, "Second LedgerEntry ID should be populated")
        assertEquals(authLedgerEntry, result[0], "First result should be AUTH_HOLD ledger entry")
        assertEquals(captureLedgerEntry, result[1], "Second result should be CAPTURE ledger entry")
        
        // ========== Then - Verify Journal Entries Created ==========
        verify(exactly = 2) { ledgerMapper.insertJournalEntry(any()) }
        assertEquals(2, capturedJournalEntries.size, "Should capture 2 journal entries")
        
        // Verify AUTH_HOLD journal entry
        val authJournalEntry = capturedJournalEntries.find { it.id == "AUTH:$paymentOrderId" }
            ?: throw AssertionError("AUTH_HOLD journal entry not found")
        assertEquals("AUTH_HOLD", authJournalEntry.txType)
        assertEquals("Authorization Hold", authJournalEntry.name)
        
        // Verify CAPTURE journal entry
        val captureJournalEntry = capturedJournalEntries.find { it.id == "CAPTURE:$paymentOrderId" }
            ?: throw AssertionError("CAPTURE journal entry not found")
        assertEquals("CAPTURE", captureJournalEntry.txType)
        assertEquals("Payment Capture", captureJournalEntry.name)
        
        // ========== Then - Verify Ledger Entries Created ==========
        verify(exactly = 2) { ledgerMapper.insertLedgerEntry(any()) }
        assertEquals(2, capturedLedgerEntities.size, "Should capture 2 ledger entry entities")
        
        // Verify first ledger entry (AUTH_HOLD)
        val authLedgerEntity = capturedLedgerEntities.find { it.journalId == "AUTH:$paymentOrderId" }
            ?: throw AssertionError("AUTH_HOLD ledger entry entity not found")
        assertEquals(1001L, authLedgerEntity.id)
        assertEquals("AUTH:$paymentOrderId", authLedgerEntity.journalId)
        
        // Verify second ledger entry (CAPTURE)
        val captureLedgerEntity = capturedLedgerEntities.find { it.journalId == "CAPTURE:$paymentOrderId" }
            ?: throw AssertionError("CAPTURE ledger entry entity not found")
        assertEquals(1002L, captureLedgerEntity.id)
        assertEquals("CAPTURE:$paymentOrderId", captureLedgerEntity.journalId)
        
        // ========== Then - Verify All Postings Created (2 from AUTH + 4 from CAPTURE = 6 total) ==========
        verify(exactly = 6) { ledgerMapper.insertPosting(any()) }
        assertEquals(6, capturedPostings.size, "Should capture 6 postings total")
        
        // Verify AUTH_HOLD postings (2 postings)
        val authPostings = capturedPostings.filter { it.journalId == "AUTH:$paymentOrderId" }
        assertEquals(2, authPostings.size, "AUTH_HOLD should have 2 postings")
        
        // AUTH_HOLD Posting 1: AUTH_RECEIVABLE.GLOBAL.USD DEBIT
        val authReceivableDebit = authPostings.find { 
            it.accountCode == "AUTH_RECEIVABLE.GLOBAL.USD" && it.direction == "DEBIT"
        } ?: throw AssertionError("AUTH_RECEIVABLE DEBIT posting not found")
        assertEquals("AUTH_RECEIVABLE", authReceivableDebit.accountType)
        assertEquals(10000L, authReceivableDebit.amount)
        assertEquals("USD", authReceivableDebit.currency)
        
        // AUTH_HOLD Posting 2: AUTH_LIABILITY.GLOBAL.USD CREDIT
        val authLiabilityCredit = authPostings.find { 
            it.accountCode == "AUTH_LIABILITY.GLOBAL.USD" && it.direction == "CREDIT"
        } ?: throw AssertionError("AUTH_LIABILITY CREDIT posting not found")
        assertEquals("AUTH_LIABILITY", authLiabilityCredit.accountType)
        assertEquals(10000L, authLiabilityCredit.amount)
        assertEquals("USD", authLiabilityCredit.currency)
        
        // Verify CAPTURE postings (4 postings)
        val capturePostings = capturedPostings.filter { it.journalId == "CAPTURE:$paymentOrderId" }
        assertEquals(4, capturePostings.size, "CAPTURE should have 4 postings")
        
        // CAPTURE Posting 1: AUTH_RECEIVABLE.GLOBAL.USD CREDIT
        val authReceivableCredit = capturePostings.find { 
            it.accountCode == "AUTH_RECEIVABLE.GLOBAL.USD" && it.direction == "CREDIT"
        } ?: throw AssertionError("AUTH_RECEIVABLE CREDIT posting not found")
        assertEquals("AUTH_RECEIVABLE", authReceivableCredit.accountType)
        assertEquals(10000L, authReceivableCredit.amount)
        assertEquals("USD", authReceivableCredit.currency)
        
        // CAPTURE Posting 2: AUTH_LIABILITY.GLOBAL.USD DEBIT
        val authLiabilityDebit = capturePostings.find { 
            it.accountCode == "AUTH_LIABILITY.GLOBAL.USD" && it.direction == "DEBIT"
        } ?: throw AssertionError("AUTH_LIABILITY DEBIT posting not found")
        assertEquals("AUTH_LIABILITY", authLiabilityDebit.accountType)
        assertEquals(10000L, authLiabilityDebit.amount)
        assertEquals("USD", authLiabilityDebit.currency)
        
        // CAPTURE Posting 3: MERCHANT_ACCOUNT.{merchantId}.USD CREDIT
        val merchantCredit = capturePostings.find { 
            it.accountCode == "MERCHANT_ACCOUNT.$merchantId.USD" && it.direction == "CREDIT"
        } ?: throw AssertionError("MERCHANT_ACCOUNT CREDIT posting not found")
        assertEquals("MERCHANT_ACCOUNT", merchantCredit.accountType)
        assertEquals(10000L, merchantCredit.amount)
        assertEquals("USD", merchantCredit.currency)
        
        // CAPTURE Posting 4: PSP_RECEIVABLES.GLOBAL.USD DEBIT
        val pspReceivablesDebit = capturePostings.find { 
            it.accountCode == "PSP_RECEIVABLES.GLOBAL.USD" && it.direction == "DEBIT"
        } ?: throw AssertionError("PSP_RECEIVABLES DEBIT posting not found")
        assertEquals("PSP_RECEIVABLES", pspReceivablesDebit.accountType)
        assertEquals(10000L, pspReceivablesDebit.amount)
        assertEquals("USD", pspReceivablesDebit.currency)
    }

    // ==================== Test 2: Duplicate Journal Entry ====================

    @Test
    fun `postLedgerEntriesAtomic should skip postings when insertJournalEntry returns 0`() {
        // Given - AUTH_HOLD_AND_CAPTURE creates multiple journal entries
        val amount = Amount.of(5000L, Currency("EUR"))
        val merchantAccount = Account.mock(AccountType.MERCHANT_ACCOUNT, "merchant-456", "EUR")
        val authReceivable = Account.mock(AccountType.AUTH_RECEIVABLE, "GLOBAL", "EUR")
        val authLiability = Account.mock(AccountType.AUTH_LIABILITY, "GLOBAL", "EUR")
        val pspReceivable = Account.mock(AccountType.PSP_RECEIVABLES, "GLOBAL", "EUR")
        val journalEntryList = JournalEntry.authHoldAndCapture(
            "PAY-789", 
            amount,
            authReceivable,
            authLiability,
            merchantAccount,
            pspReceivable
        )
        // Test with the capture journal entry (4 postings)
        val journalEntry = journalEntryList.last() // Capture is the second entry
        val ledgerEntry = ledgerEntryFactory.create(journalEntry)
        
        // Setup: mapper returns 0 indicating duplicate
        every { ledgerMapper.insertJournalEntry(any()) } returns 0

        // When
        val result = adapter.postLedgerEntriesAtomic(listOf(ledgerEntry))
        
        // Then - verify empty list returned when duplicate detected
        assertEquals(emptyList<LedgerEntry>(), result)

        // Then - verify journal entry insert was attempted (CAPTURE is second in authHoldAndCapture)
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
    fun `postLedgerEntriesAtomic should not insert postings when insertJournalEntry throws exception`() {
        // Given - AUTH_HOLD_AND_CAPTURE creates multiple journal entries
        val amount = Amount.of(10000L, Currency("USD"))
        val merchantAccount = Account.mock(AccountType.MERCHANT_ACCOUNT, "merchant-999", "USD")
        val authReceivable = Account.mock(AccountType.AUTH_RECEIVABLE, "GLOBAL", "USD")
        val authLiability = Account.mock(AccountType.AUTH_LIABILITY, "GLOBAL", "USD")
        val pspReceivable = Account.mock(AccountType.PSP_RECEIVABLES, "GLOBAL", "USD")
        val journalEntryList = JournalEntry.authHoldAndCapture(
            "PAY-999", 
            amount,
            authReceivable,
            authLiability,
            merchantAccount,
            pspReceivable
        )
        // Test with the first journal entry (AUTH_HOLD)
        val journalEntry = journalEntryList.first()
        val ledgerEntry = ledgerEntryFactory.create(journalEntry)
        
        // Setup: mapper throws exception
        every { ledgerMapper.insertJournalEntry(any()) } throws RuntimeException("Database connection failed")

        // When/Then - verify exception is thrown
        org.junit.jupiter.api.assertThrows<RuntimeException> {
            adapter.postLedgerEntriesAtomic(listOf(ledgerEntry))
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
