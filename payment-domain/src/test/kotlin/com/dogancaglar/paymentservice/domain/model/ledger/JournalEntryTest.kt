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
    
    private val eur = Currency("EUR")
    val platformCashAccount = Account.create(AccountType.PLATFORM_CASH, "merchantId")
    val merchantAccount = Account.create(AccountType.MERCHANT_PAYABLE, "merchantId")
    val pspFeeExpenseAccount = Account.create(AccountType.PSP_FEE_EXPENSE, "GLOBAL")
    val commissionRevenueAccount = Account.create(AccountType.PLATFORM_COMMISSION_REVENUE, "GLOBAL")
    val authLiabilityAccount = Account.create(AccountType.AUTH_LIABILITY, "GLOBAL")
    val authReceivableAccount = Account.create(AccountType.AUTH_RECEIVABLE, "GLOBAL")
    val pspReceivableAccount = Account.create(AccountType.PSP_RECEIVABLES, "GLOBAL")

    @Test
    fun `authHold should create balanced entry with AUTH_RECEIVABLE debit and AUTH_LIABILITY credit`() {
        val amount = Amount.of(10_000, eur)
        val entryList = JournalEntry.authHold("PAY-1", amount, authReceivableAccount, authLiabilityAccount)
        val entry = entryList.first()
        
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
        val amount = Amount.of(10_000, eur)
        val entryList = JournalEntry.capture("PAY-2", amount, authReceivableAccount, authLiabilityAccount, merchantAccount, pspReceivableAccount)
        val entry = entryList.first()
        
        val drAccounts = entry.postings.filterIsInstance<Posting.Debit>().map { it.account.type }
        val crAccounts = entry.postings.filterIsInstance<Posting.Credit>().map { it.account.type }
        
        assertTrue(entry.postings.isNotEmpty())
        assertTrue(AccountType.PSP_RECEIVABLES in drAccounts)
        assertTrue(AccountType.AUTH_RECEIVABLE in crAccounts)
        assertTrue(AccountType.AUTH_LIABILITY in drAccounts)
        assertTrue(AccountType.MERCHANT_PAYABLE in crAccounts)
        
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
    fun `settlement should increase cash account by settled amount and also record psp fee as expense and balance psp receivable`() {
        val gross = Amount.of(10_000, Currency("EUR"))
        val settledAmount = Amount.of(9500, Currency("EUR"))
        val entryList = JournalEntry.settlement("PAY-3", gross, settledAmount,platformCashAccount,pspFeeExpenseAccount,pspReceivableAccount)
        val entry = entryList.first()

        assertEquals(0L, entry.postings.sumOf { it.getSignedAmount().quantity })
        assertTrue(entry.postings.any { it.account.type == AccountType.PLATFORM_CASH })
        assertTrue(entry.postings.any { it.account.type == AccountType.PSP_RECEIVABLES })
        assertTrue(entry.postings.any { it.account.type == AccountType.PSP_FEE_EXPENSE })
    }

    @Test
    fun `commissiionFee should reduce merchant liability and record commission revenue`() {
        val commissionFee = Amount.of(200, Currency("EUR"))
        val entryList = JournalEntry.commissionFeRegistered("PAY-4", commissionFee, commissionRevenueAccount,merchantAccount)
        val entry = entryList.first()

        assertEquals(0L, entry.postings.sumOf { it.getSignedAmount().quantity })
        val debit = entry.postings.filterIsInstance<Posting.Debit>().first()
        val credit = entry.postings.filterIsInstance<Posting.Credit>().first()

        assertEquals(merchantAccount.type, debit.account.type)
        assertEquals(AccountType.PLATFORM_COMMISSION_REVENUE, credit.account.type)
    }

    @Test
    fun `payout should decrease platform cash and merchant payable`() {
        val payout = Amount.of(9_800, Currency("EUR"))
        val entryList = JournalEntry.payout("MERCHANT-1", payout, merchantAccount, platformCashAccount)
        val entry = entryList.first()
        
        val netByAccount = entry.postings
            .groupBy { it.account.accountCode }
            .mapValues { (_, posts) -> posts.sumOf { it.getSignedAmount().quantity } }

        assertEquals(-9800, netByAccount[merchantAccount.accountCode])
        assertEquals(-9800, netByAccount[platformCashAccount.accountCode])

        assertTrue(entry.postings.any { it.account.accountCode == merchantAccount.accountCode })
        assertTrue(entry.postings.any { it.account.accountCode == platformCashAccount.accountCode })
    }

    @Test
    fun `fullFlow should net platform commission fee as profit and merchant payable cleared`() {
        val capturedAmount = Amount.of(10_000, eur)//100 gross
        val settledAmount = Amount.of(9800, eur) // 2 psp fee as expense
        val commissionFeeRevenue = Amount.of(400, eur) //our commission as reevenue
        val payout = capturedAmount-commissionFeeRevenue

        val entryLists = listOf(
            JournalEntry.capture("PAY-5", capturedAmount, authReceivableAccount, authLiabilityAccount, merchantAccount, pspReceivableAccount),
            JournalEntry.settlement("PAY-5", capturedAmount, settledAmount, platformCashAccount,pspFeeExpenseAccount,pspReceivableAccount),
            JournalEntry.commissionFeRegistered("PAY-5", commissionFeeRevenue, commissionRevenueAccount,merchantAccount),
            JournalEntry.payout("MERCHANT-1", capturedAmount-commissionFeeRevenue, merchantAccount, platformCashAccount)
        )
        val allPostings = entryLists.flatMap { entryList -> entryList.flatMap { it.postings } }

        val totalDebits = allPostings.filterIsInstance<Posting.Debit>().sumOf { it.amount.quantity }
        val totalCredits = allPostings.filterIsInstance<Posting.Credit>().sumOf { it.amount.quantity }
        
        val netByAccount = allPostings
            .groupBy { it.account.accountCode }
            .mapValues { (_, posts) -> posts.sumOf { it.getSignedAmount().quantity } }

        assertEquals(totalDebits, totalCredits, "Global double-entry check failed")

        assertEquals(200, netByAccount[platformCashAccount.accountCode]) // profit 200
        assertEquals(200L, netByAccount[pspFeeExpenseAccount.accountCode]) // psp fee expense 200
        assertEquals(400, netByAccount[commissionRevenueAccount.accountCode]) // revenue= 400
    }

    @Test
    fun `failedPayment should return empty list`() {
        val entryList = JournalEntry.failedPayment("PAY-FAILED", Amount.of(10_000, Currency("USD")))

        assertTrue(entryList.isEmpty())
    }

    @Test
    fun `all factory methods should create entries with correct IDs`() {
        //
    }
}

