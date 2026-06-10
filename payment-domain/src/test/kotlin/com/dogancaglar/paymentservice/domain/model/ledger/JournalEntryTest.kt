package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for JournalEntry factory methods.
 * 
 * Verifies:
 * - All journal entries are balanced (debits = credits)
 * - Correct accounts are used for each transaction type
 * - Exact postings are generated as expected
 */
class JournalEntryTest {
    
    private val eur = Currency("EUR")
    private val paymentId = PaymentId(100L)
    private val txId = TxId(1L)
    private val journalId = "PAY-1"

    private val platformCashAccount = Account.create(AccountType.PLATFORM_CASH, "PLATFORM_CASH.GLOBAL.EUR")
    private val merchantAccount = Account.create(AccountType.MERCHANT_GROSS_CAPTURE_SUSPENSE, "MERCHANT_GROSS_CAPTURE_SUSPENSE.merchantId.EUR")
    private val merchantGrossPool = Account.create(AccountType.MERCHANT_GROSS_CAPTURE_SUSPENSE, "MERCHANT_GROSS_CAPTURE_SUSPENSE.merchantId.EUR")
    private val subSellerAccount = Account.create(AccountType.MARKETPLACE_SELLER_BALANCE_ACCOUNT, "subSellerId.EUR")
    private val pspFeeExpenseAccount = Account.create(AccountType.PSP_FEE_EXPENSE, "PSP_FEE_EXPENSE.GLOBAL.EUR")
    private val commissionRevenueAccount = Account.create(AccountType.PLATFORM_OPERATIONAL_REVENUE, "PLATFORM_OPERATIONAL_REVENUE.GLOBAL.EUR")
    private val commissionEscrowAccount = Account.create(AccountType.PLATFORM_COMMISSION_ESCROW, "PLATFORM_COMMISSION_ESCROW.merchantId.EUR")
    private val authLiabilityAccount = Account.create(AccountType.AUTH_LIABILITY, "AUTH_LIABILITY.GLOBAL.EUR")
    private val authReceivableAccount = Account.create(AccountType.AUTH_RECEIVABLE, "AUTH_RECEIVABLE.GLOBAL.EUR")
    private val pspReceivableAccount = Account.create(AccountType.PSP_RECEIVABLES, "PSP_RECEIVABLES.GLOBAL.EUR")

    private fun assertBalanced(entry: JournalEntry) {
        val totalDebits = entry.postings.filterIsInstance<Posting.Debit>().sumOf { it.amount.quantity }
        val totalCredits = entry.postings.filterIsInstance<Posting.Credit>().sumOf { it.amount.quantity }
        assertEquals(totalDebits, totalCredits, "Journal entry is not balanced")
        assertTrue(totalDebits > 0, "Journal entry has zero amount")
    }

    private fun assertPostingContains(entry: JournalEntry, account: Account, isDebit: Boolean, expectedAmount: Long) {
        val matchingPostings = entry.postings.filter { it.account == account && ((isDebit && it is Posting.Debit) || (!isDebit && it is Posting.Credit)) }
        assertTrue(matchingPostings.isNotEmpty(), "Expected ${if(isDebit) "Debit" else "Credit"} for account ${account.type} not found")
        assertEquals(expectedAmount, matchingPostings.sumOf { it.amount.quantity }, "Amount mismatch for account ${account.type}")
    }

    @Test
    fun `authHold creates correct postings and balances`() {
        val amount = Amount.of(10_000, eur)
        val entries = JournalEntry.authHold(
            paymentId = paymentId,
            txId = txId,
            journalIdentifier = journalId,
            authorizedAmount = amount,
            authReceivable = authReceivableAccount,
            authLiability = authLiabilityAccount
        )
        
        assertEquals(1, entries.size)
        val entry = entries.first()
        
        assertBalanced(entry)
        assertEquals(JournalType.AUTHORIZATION, entry.journalType)
        assertPostingContains(entry, authReceivableAccount, isDebit = true, expectedAmount = 10_000)
        assertPostingContains(entry, authLiabilityAccount, isDebit = false, expectedAmount = 10_000)
    }

    @Test
    fun `captureGrossAsset creates correct postings and balances`() {
        val amount = Amount.of(10_000, eur)
        val entries = JournalEntry.captureGrossAsset(
            paymentId = paymentId,
            txId = txId,
            journalIdentifier = journalId,
            capturedAmount = amount,
            authReceivable = authReceivableAccount,
            authLiability = authLiabilityAccount,
            merchantGrossPool = merchantGrossPool,
            pspReceivable = pspReceivableAccount
        )
        
        assertEquals(1, entries.size)
        val entry = entries.first()
        
        assertBalanced(entry)
        assertEquals(JournalType.CAPTURE, entry.journalType)
        assertPostingContains(entry, authLiabilityAccount, isDebit = true, expectedAmount = 10_000)
        assertPostingContains(entry, authReceivableAccount, isDebit = false, expectedAmount = 10_000)
        assertPostingContains(entry, pspReceivableAccount, isDebit = true, expectedAmount = 10_000)
        assertPostingContains(entry, merchantGrossPool, isDebit = false, expectedAmount = 10_000)
    }


    @Test
    fun `internalTransfer creates correct postings for a single transfer and balances`() {
        val amount = Amount.of(5_000, eur)
        val entries = JournalEntry.internalTransfer(
            paymentId = paymentId,
            txId = txId,
            journalIdentifier = journalId,
            amount = amount,
            sourceAccount = merchantGrossPool,
            targetAccount = subSellerAccount
        )
        
        assertEquals(1, entries.size)
        val entry = entries.first()
        
        assertBalanced(entry)
        assertEquals(JournalType.INTERNAL_TRANSFER, entry.journalType)
        assertPostingContains(entry, merchantGrossPool, isDebit = true, expectedAmount = 5_000)
        assertPostingContains(entry, subSellerAccount, isDebit = false, expectedAmount = 5_000)
    }

    @Test
    fun `refund creates correct postings and balances`() {
        val amount = Amount.of(10_000, eur)
        val entries = JournalEntry.refund(
            paymentId = paymentId,
            txId = txId,
            journalIdentifier = journalId,
            refundedAmount = amount,
            authReceivable = authReceivableAccount,
            authLiability = authLiabilityAccount,
            merchantGrossPool = merchantGrossPool,
            pspReceivable = pspReceivableAccount
        )
        
        assertEquals(1, entries.size)
        val entry = entries.first()
        
        assertBalanced(entry)
        assertEquals(JournalType.REFUND, entry.journalType)
        assertPostingContains(entry, authLiabilityAccount, isDebit = true, expectedAmount = 10_000)
        assertPostingContains(entry, authReceivableAccount, isDebit = false, expectedAmount = 10_000)
        assertPostingContains(entry, merchantGrossPool, isDebit = true, expectedAmount = 10_000)
        assertPostingContains(entry, pspReceivableAccount, isDebit = false, expectedAmount = 10_000)
    }

    @Test
    fun `settlementLineItem creates correct postings and balances`() {
        val gross = Amount.of(10_000, eur)
        val settledAmount = Amount.of(9_500, eur)
        val feeAmount = Amount.of(500, eur)
        
        val entries = JournalEntry.settlementLineItem(
            paymentId = paymentId,
            settlementTxId = txId,
            journalIdentifier = journalId,
            grossAmount = gross,
            netCashAmount = settledAmount,
            pspFeeAmount = feeAmount,
            platformCash = platformCashAccount,
            pspFeeExpense = pspFeeExpenseAccount,
            pspReceivable = pspReceivableAccount
        )
        
        assertEquals(1, entries.size)
        val entry = entries.first()
        
        assertBalanced(entry)
        assertEquals(JournalType.SETTLEMENT, entry.journalType)
        assertPostingContains(entry, pspFeeExpenseAccount, isDebit = true, expectedAmount = 500)
        assertPostingContains(entry, platformCashAccount, isDebit = true, expectedAmount = 9_500)
        assertPostingContains(entry, pspReceivableAccount, isDebit = false, expectedAmount = 10_000)
    }

    @Test
    fun `commissionFeeRegistered creates correct postings and balances`() {
        val amount = Amount.of(200, eur)
        val entries = JournalEntry.commissionFeeRegistered(
            paymentId = paymentId,
            txId = txId,
            journalIdentifier = journalId,
            commissionFee = amount,
            commissionEscrowAccount = commissionEscrowAccount,
            merchantGrossPool = merchantAccount
        )
        
        assertEquals(1, entries.size)
        val entry = entries.first()
        
        assertBalanced(entry)
        assertEquals(JournalType.COMMISSION_FEE, entry.journalType)
        assertPostingContains(entry, merchantAccount, isDebit = true, expectedAmount = 200)
        assertPostingContains(entry, commissionEscrowAccount, isDebit = false, expectedAmount = 200)
    }

    @Test
    fun `payout creates correct postings and balances`() {
        val amount = Amount.of(9_800, eur)
        val entries = JournalEntry.payout(
            paymentId = paymentId,
            txId = txId,
            journalIdentifier = journalId,
            payoutAmount = amount,
            sourceBalanceAccount = merchantAccount,
            platformCash = platformCashAccount
        )
        
        assertEquals(1, entries.size)
        val entry = entries.first()
        
        assertBalanced(entry)
        assertEquals(JournalType.PAYOUT, entry.journalType)
        assertPostingContains(entry, merchantAccount, isDebit = true, expectedAmount = 9_800)
        assertPostingContains(entry, platformCashAccount, isDebit = false, expectedAmount = 9_800)
    }
}
