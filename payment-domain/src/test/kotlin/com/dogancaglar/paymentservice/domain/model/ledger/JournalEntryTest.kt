package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for JournalEntry factory methods.
 * 
 * Verifies:
 * - All journal entries are balanced (debits = credits)
 * - Correct accounts are used for each transaction type
 * - Postings are properly configured
 */
class JournalEntryTest {
    
    val acquirerAccount = Account.create(AccountType.ACQUIRER_ACCOUNT, "merchantId")
    val merchantAccount = Account.create(AccountType.MERCHANT_ACCOUNT, "merchantId")
    val pspFeeRevenueAccount = Account.create(AccountType.PROCESSING_FEE_REVENUE)
    val authLiabilityAccount = Account.create(AccountType.AUTH_LIABILITY)
    val authReceivableAccount = Account.create(AccountType.AUTH_RECEIVABLE)
    val pspReceivableAccount = Account.create(AccountType.PSP_RECEIVABLES)

    @Test
    fun `authHold should create balanced entry with AUTH_RECEIVABLE debit and AUTH_LIABILITY credit`() {
        val amount = Amount.of(10_000, Currency("EUR"))
        val entry = JournalEntry.authHold("PAY-1", amount)
        
        val totalDebitAmount = entry.postings.filterIsInstance<Posting.Debit>().sumOf { it.amount.quantity }
        val totalCreditAmount = entry.postings.filterIsInstance<Posting.Credit>().sumOf { it.amount.quantity }
        val netBalanceByAccount = entry.postings
            .groupBy { it.account.accountCode }
            .mapValues { (_, postings) -> postings.sumOf { it.getSignedAmount().quantity } }
        
        assertTrue(entry.postings.isNotEmpty())
        assertEquals(totalDebitAmount, totalCreditAmount, "Entry must be balanced")
        assertTrue(entry.postings.any { it.account.type == AccountType.AUTH_RECEIVABLE })
        assertTrue(entry.postings.any { it.account.type == AccountType.AUTH_LIABILITY })
        assertEquals(10_000, netBalanceByAccount[authLiabilityAccount.accountCode])
        assertEquals(10_000, netBalanceByAccount[authReceivableAccount.accountCode])
    }

    @Test
    fun `capture should balance and shift from auth to psp receivables, and increase merchant payable`() {
        val amount = Amount.of(10_000, Currency("EUR"))
        val entry = JournalEntry.capture("PAY-2", amount, merchantAccount)
        
        val drAccounts = entry.postings.filterIsInstance<Posting.Debit>().map { it.account.type }
        val crAccounts = entry.postings.filterIsInstance<Posting.Credit>().map { it.account.type }
        
        assertTrue(entry.postings.isNotEmpty())
        assertTrue(AccountType.PSP_RECEIVABLES in drAccounts)
        assertTrue(AccountType.AUTH_RECEIVABLE in crAccounts)
        assertTrue(AccountType.AUTH_LIABILITY in drAccounts)
        assertTrue(AccountType.MERCHANT_ACCOUNT in crAccounts)
        
        val netAmountByAccount = entry.postings
            .groupBy { it.account.accountCode }
            .mapValues { (_, postings) -> postings.sumOf { it.getSignedAmount().quantity } }
        
        // Global balance should be zero
        assertEquals(0L, entry.postings.sumOf { it.getSignedAmount().quantity })
        // Merchant payable up
        assertEquals(10000, netAmountByAccount[merchantAccount.accountCode])
        // PSP receivable up
        assertEquals(10000, netAmountByAccount[pspReceivableAccount.accountCode])
        // Balance out auths
        assertEquals(-10_000, netAmountByAccount[authLiabilityAccount.accountCode])
        assertEquals(-10_000, netAmountByAccount[authReceivableAccount.accountCode])
    }

    @Test
    fun `settlement should record acquirer inflow and fee expenses`() {
        val gross = Amount.of(10_000, Currency("EUR"))
        val interchange = Amount.of(300, Currency("EUR"))
        val scheme = Amount.of(200, Currency("EUR"))
        val entry = JournalEntry.settlement("PAY-3", gross, interchange, scheme, acquirerAccount)

        assertEquals(0L, entry.postings.sumOf { it.getSignedAmount().quantity })
        assertTrue(entry.postings.any { it.account.type == AccountType.ACQUIRER_ACCOUNT })
        assertTrue(entry.postings.any { it.account.type == AccountType.PSP_RECEIVABLES })
        assertTrue(entry.postings.any { it.account.type == AccountType.SCHEME_FEES })
        assertTrue(entry.postings.any { it.account.type == AccountType.INTERCHANGE_FEES })
    }

    @Test
    fun `feeRegistered should reduce merchant liability and record PSP revenue`() {
        val fee = Amount.of(200, Currency("EUR"))
        val entry = JournalEntry.feeRegistered("PAY-4", fee, merchantAccount)

        assertEquals(0L, entry.postings.sumOf { it.getSignedAmount().quantity })
        val debit = entry.postings.filterIsInstance<Posting.Debit>().first()
        val credit = entry.postings.filterIsInstance<Posting.Credit>().first()

        assertEquals(merchantAccount.type, debit.account.type)
        assertEquals(AccountType.PROCESSING_FEE_REVENUE, credit.account.type)
    }

    @Test
    fun `payout should decrease acquirer cash and merchant payable`() {
        val payout = Amount.of(9_800, Currency("EUR"))
        val entry = JournalEntry.payout("MERCHANT-1", payout, merchantAccount, acquirerAccount)
        
        val netByAccount = entry.postings
            .groupBy { it.account.accountCode }
            .mapValues { (_, posts) -> posts.sumOf { it.getSignedAmount().quantity } }

        assertEquals(-9800, netByAccount[merchantAccount.accountCode])
        assertEquals(-9800, netByAccount[acquirerAccount.accountCode])

        assertTrue(entry.postings.any { it.account.accountCode == merchantAccount.accountCode })
        assertTrue(entry.postings.any { it.account.accountCode == acquirerAccount.accountCode })
    }

    @Test
    fun `fullFlow should net PSP fee as profit and merchant payable cleared`() {
        val pspFeeRevenueAccount = Account.create(AccountType.PROCESSING_FEE_REVENUE)
        val eur = Currency("EUR")
        val capture = Amount.of(10_000, eur)
        val settlement = Amount.of(10_000, eur)
        val fee = Amount.of(200, eur)
        val payout = Amount.of(9_800, eur)

        val entries = listOf(
            JournalEntry.capture("PAY-5", capture, merchantAccount),
            JournalEntry.settlement("PAY-5", settlement, Amount.of(0, eur), Amount.of(0, eur), acquirerAccount),
            JournalEntry.feeRegistered("PAY-5", fee, merchantAccount),
            JournalEntry.payout("MERCHANT-1", payout, merchantAccount, acquirerAccount)
        )

        val allPostings = entries.flatMap { it.postings }

        val totalDebits = allPostings.filterIsInstance<Posting.Debit>().sumOf { it.amount.quantity }
        val totalCredits = allPostings.filterIsInstance<Posting.Credit>().sumOf { it.amount.quantity }
        
        val netByAccount = allPostings
            .groupBy { it.account.accountCode }
            .mapValues { (_, posts) -> posts.sumOf { it.getSignedAmount().quantity } }

        assertEquals(totalDebits, totalCredits, "Global double-entry check failed")
        // PSP keeps fee in acquirer account and recognizes revenue
        assertEquals(200L, netByAccount[acquirerAccount.accountCode])
        assertEquals(200L, netByAccount[pspFeeRevenueAccount.accountCode])
    }

    @Test
    fun `failedPayment should return empty list`() {
        val entries = JournalEntry.failedPayment("PAY-FAILED", Amount.of(10_000, Currency("USD")))
        
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `all factory methods should create entries with correct IDs`() {
        val usd = Currency("USD")
        val amount = Amount.of(1000L, usd)
        val merchantAccount = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-123")
        val acquirerAccount = Account.create(AccountType.ACQUIRER_ACCOUNT, "seller-123")
        
        assertEquals("AUTH:PAY-123", JournalEntry.authHold("PAY-123", amount).id)
        assertEquals("CAPTURE:PAY-123", JournalEntry.capture("PAY-123", amount, merchantAccount).id)
        assertEquals("SETTLEMENT:PAY-123", JournalEntry.settlement("PAY-123", amount, Amount.of(0L, usd), Amount.of(0L, usd), acquirerAccount).id)
        assertEquals("PSP-FEE:PAY-123", JournalEntry.feeRegistered("PAY-123", amount, merchantAccount).id)
        assertEquals("PAYOUT:PAY-123", JournalEntry.payout("PAY-123", amount, merchantAccount, acquirerAccount).id)
    }
}

