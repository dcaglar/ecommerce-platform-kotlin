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
        val entryList = JournalEntry.authHold("PAY-1", amount)
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
        val amount = Amount.of(10_000, Currency("EUR"))
        val entryList = JournalEntry.capture("PAY-2", amount, merchantAccount)
        val entry = entryList.first()
        
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
    fun `authHoldAndCapture should create both auth hold and capture entries in sequence`() {
        val amount = Amount.of(10_000, Currency("EUR"))
        val entryList = JournalEntry.authHoldAndCapture("PAY-COMBO", amount, merchantAccount)
        
        // Should return 2 entries: authHold + capture
        assertEquals(2, entryList.size)
        
        // First entry should be authHold
        val authEntry = entryList[0]
        assertEquals("AUTH:PAY-COMBO", authEntry.id)
        assertEquals(JournalType.AUTH_HOLD, authEntry.txType)
        
        // Verify auth entry is balanced
        val authDebitAmount = authEntry.postings.filterIsInstance<Posting.Debit>().sumOf { it.amount.quantity }
        val authCreditAmount = authEntry.postings.filterIsInstance<Posting.Credit>().sumOf { it.amount.quantity }
        assertEquals(authDebitAmount, authCreditAmount, "Auth entry must be balanced")
        
        assertTrue(authEntry.postings.any { it.account.type == AccountType.AUTH_RECEIVABLE })
        assertTrue(authEntry.postings.any { it.account.type == AccountType.AUTH_LIABILITY })
        
        // Second entry should be capture
        val captureEntry = entryList[1]
        assertEquals("CAPTURE:PAY-COMBO", captureEntry.id)
        assertEquals(JournalType.CAPTURE, captureEntry.txType)
        
        // Verify capture entry is balanced
        val captureDebitAmount = captureEntry.postings.filterIsInstance<Posting.Debit>().sumOf { it.amount.quantity }
        val captureCreditAmount = captureEntry.postings.filterIsInstance<Posting.Credit>().sumOf { it.amount.quantity }
        assertEquals(captureDebitAmount, captureCreditAmount, "Capture entry must be balanced")
        
        assertTrue(captureEntry.postings.any { it.account.type == AccountType.AUTH_RECEIVABLE })
        assertTrue(captureEntry.postings.any { it.account.type == AccountType.AUTH_LIABILITY })
        assertTrue(captureEntry.postings.any { it.account.type == AccountType.MERCHANT_ACCOUNT })
        assertTrue(captureEntry.postings.any { it.account.type == AccountType.PSP_RECEIVABLES })
        
        // Combined postings should balance out auth accounts and show merchant/PSP flow
        val allPostings = entryList.flatMap { it.postings }
        val netByAccount = allPostings
            .groupBy { it.account.accountCode }
            .mapValues { (_, posts) -> posts.sumOf { it.getSignedAmount().quantity } }
        
        // Auth accounts should balance out (debit and credit cancel)
        assertEquals(0L, netByAccount[authLiabilityAccount.accountCode] ?: 0L)
        assertEquals(0L, netByAccount[authReceivableAccount.accountCode] ?: 0L)
        // Merchant should have positive balance (receivable)
        assertEquals(10_000, netByAccount[merchantAccount.accountCode])
        // PSP should have receivable
        assertEquals(10_000, netByAccount[pspReceivableAccount.accountCode])
    }

    @Test
    fun `settlement should record acquirer inflow and fee expenses`() {
        val gross = Amount.of(10_000, Currency("EUR"))
        val interchange = Amount.of(300, Currency("EUR"))
        val scheme = Amount.of(200, Currency("EUR"))
        val entryList = JournalEntry.settlement("PAY-3", gross, interchange, scheme, acquirerAccount)
        val entry = entryList.first()

        assertEquals(0L, entry.postings.sumOf { it.getSignedAmount().quantity })
        assertTrue(entry.postings.any { it.account.type == AccountType.ACQUIRER_ACCOUNT })
        assertTrue(entry.postings.any { it.account.type == AccountType.PSP_RECEIVABLES })
        assertTrue(entry.postings.any { it.account.type == AccountType.SCHEME_FEES })
        assertTrue(entry.postings.any { it.account.type == AccountType.INTERCHANGE_FEES })
    }

    @Test
    fun `feeRegistered should reduce merchant liability and record PSP revenue`() {
        val fee = Amount.of(200, Currency("EUR"))
        val entryList = JournalEntry.feeRegistered("PAY-4", fee, merchantAccount)
        val entry = entryList.first()

        assertEquals(0L, entry.postings.sumOf { it.getSignedAmount().quantity })
        val debit = entry.postings.filterIsInstance<Posting.Debit>().first()
        val credit = entry.postings.filterIsInstance<Posting.Credit>().first()

        assertEquals(merchantAccount.type, debit.account.type)
        assertEquals(AccountType.PROCESSING_FEE_REVENUE, credit.account.type)
    }

    @Test
    fun `payout should decrease acquirer cash and merchant payable`() {
        val payout = Amount.of(9_800, Currency("EUR"))
        val entryList = JournalEntry.payout("MERCHANT-1", payout, merchantAccount, acquirerAccount)
        val entry = entryList.first()
        
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

        val entryLists = listOf(
            JournalEntry.capture("PAY-5", capture, merchantAccount),
            JournalEntry.settlement("PAY-5", settlement, Amount.of(1, eur), Amount.of(1, eur), acquirerAccount),
            JournalEntry.feeRegistered("PAY-5", fee, merchantAccount),
            JournalEntry.payout("MERCHANT-1", payout, merchantAccount, acquirerAccount)
        )

        val allPostings = entryLists.flatMap { entryList -> entryList.flatMap { it.postings } }

        val totalDebits = allPostings.filterIsInstance<Posting.Debit>().sumOf { it.amount.quantity }
        val totalCredits = allPostings.filterIsInstance<Posting.Credit>().sumOf { it.amount.quantity }
        
        val netByAccount = allPostings
            .groupBy { it.account.accountCode }
            .mapValues { (_, posts) -> posts.sumOf { it.getSignedAmount().quantity } }

        assertEquals(totalDebits, totalCredits, "Global double-entry check failed")
        // PSP keeps fee in acquirer account and recognizes revenue
        // With fees of 1 each: settlement debits acquirer 9998, payout credits acquirer 9800, net = -198
        // But PSP keeps the 200 fee, so net acquirer should be 200 - 2 = 198
        // Actually: Acquirer receives 10,000 - 1 - 1 = 9,998, then pays out 9,800, net = 198
        // But the test expects 200 (the fee amount). The 2 difference is from the scheme/interchange fees.
        // Adjusted expectation: Acquirer net = 200 - 2 = 198
        assertEquals(198L, netByAccount[acquirerAccount.accountCode])
        assertEquals(200L, netByAccount[pspFeeRevenueAccount.accountCode])
    }

    @Test
    fun `failedPayment should return empty list`() {
        val entryList = JournalEntry.failedPayment("PAY-FAILED", Amount.of(10_000, Currency("USD")))

        assertTrue(entryList.isEmpty())
    }

    @Test
    fun `all factory methods should create entries with correct IDs`() {
        val usd = Currency("USD")
        val amount = Amount.of(1000L, usd)
        val merchantAccount = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-123")
        val acquirerAccount = Account.create(AccountType.ACQUIRER_ACCOUNT, "seller-123")
        
        assertEquals("AUTH:PAY-123", JournalEntry.authHold("PAY-123", amount).first().id)
        assertEquals("CAPTURE:PAY-123", JournalEntry.capture("PAY-123", amount, merchantAccount).first().id)
        assertEquals("SETTLEMENT:PAY-123", JournalEntry.settlement("PAY-123", amount, Amount.of(1L, usd), Amount.of(1L, usd), acquirerAccount).first().id)
        assertEquals("PSP-FEE:PAY-123", JournalEntry.feeRegistered("PAY-123", amount, merchantAccount).first().id)
        assertEquals("PAYOUT:PAY-123", JournalEntry.payout("PAY-123", amount, merchantAccount, acquirerAccount).first().id)
    }
}

