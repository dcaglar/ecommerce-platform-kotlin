package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.payment.PaymentSplit
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

    private val platformCashAccount = Account.create(AccountType.PLATFORM_CASH, "GLOBAL")
    private val merchantAccount = Account.create(AccountType.MARKETPLACE_OPERATOR, "merchantId")
    private val merchantGrossPool = Account.create(AccountType.MARKETPLACE_OPERATOR, "merchantId")
    private val subSellerAccount = Account.create(AccountType.MARKETPLACE_SUB_SELLER, "subSellerId")
    private val pspFeeExpenseAccount = Account.create(AccountType.PSP_FEE_EXPENSE, "GLOBAL")
    private val commissionRevenueAccount = Account.create(AccountType.PLATFORM_OPERATIONAL_REVENUE, "GLOBAL")
    private val commissionEscrowAccount = Account.create(AccountType.PLATFORM_COMMISSION_ESCROW, "GLOBAL")
    private val authLiabilityAccount = Account.create(AccountType.AUTH_LIABILITY, "GLOBAL")
    private val authReceivableAccount = Account.create(AccountType.AUTH_RECEIVABLE, "GLOBAL")
    private val pspReceivableAccount = Account.create(AccountType.PSP_RECEIVABLES, "GLOBAL")

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
        assertEquals(JournalType.AUTH_HOLD, entry.journalType)
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
    fun `executeSubSellerSplit creates correct postings for multiple splits and balances`() {
        val split1 = PaymentSplit.of(AccountType.MARKETPLACE_SUB_SELLER, "subSellerId", Amount.of(8_000, eur))
        val split2 = PaymentSplit.of(AccountType.PLATFORM_COMMISSION_ESCROW, "GLOBAL", Amount.of(2_000, eur))
        
        val entries = JournalEntry.executeSubSellerSplit(
            paymentId = paymentId,
            txId = txId,
            journalIdentifier = journalId,
            merchantGrossPool = merchantGrossPool,
            splits = listOf(split1, split2),
            resolveTargetAccount = { type, id -> if (type == AccountType.MARKETPLACE_SUB_SELLER) subSellerAccount else commissionEscrowAccount }
        )
        
        assertEquals(2, entries.size)
        
        // Check first entry (Sub-Seller)
        val entry1 = entries[0]
        assertBalanced(entry1)
        assertEquals(JournalType.INTERNAL_TRANSFER, entry1.journalType)
        assertPostingContains(entry1, merchantGrossPool, isDebit = true, expectedAmount = 8_000)
        assertPostingContains(entry1, subSellerAccount, isDebit = false, expectedAmount = 8_000)
        
        // Check second entry (Commission Escrow)
        val entry2 = entries[1]
        assertBalanced(entry2)
        assertEquals(JournalType.INTERNAL_TRANSFER, entry2.journalType)
        assertPostingContains(entry2, merchantGrossPool, isDebit = true, expectedAmount = 2_000)
        assertPostingContains(entry2, commissionEscrowAccount, isDebit = false, expectedAmount = 2_000)
    }

    @Test
    fun `internalTransfer creates correct postings for a single transfer and balances`() {
        val amount = Amount.of(5_000, eur)
        val entries = JournalEntry.internalTransfer(
            paymentId = paymentId,
            txId = txId,
            journalIdentifier = journalId,
            amount = amount,
            merchantGrossPool = merchantGrossPool,
            targetAccount = subSellerAccount,
            targetAccountType = AccountType.MARKETPLACE_SUB_SELLER,
            targetEntityId = "subSellerId"
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
    fun `settlement creates correct postings and balances`() {
        val gross = Amount.of(10_000, eur)
        val settledAmount = Amount.of(9_500, eur)
        
        val entries = JournalEntry.settlement(
            paymentId = paymentId,
            txId = txId,
            journalIdentifier = journalId,
            capturedAmount = gross,
            settledAmount = settledAmount,
            platformCashAccount = platformCashAccount,
            pspFeeExpenseAccount = pspFeeExpenseAccount,
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
            commissionFeeAccount = commissionRevenueAccount,
            merchantAccount = merchantAccount
        )
        
        assertEquals(1, entries.size)
        val entry = entries.first()
        
        assertBalanced(entry)
        assertEquals(JournalType.COMMISSION_FEE, entry.journalType)
        assertPostingContains(entry, merchantAccount, isDebit = true, expectedAmount = 200)
        assertPostingContains(entry, commissionRevenueAccount, isDebit = false, expectedAmount = 200)
    }

    @Test
    fun `payout creates correct postings and balances`() {
        val amount = Amount.of(9_800, eur)
        val entries = JournalEntry.payout(
            paymentId = paymentId,
            txId = txId,
            journalIdentifier = journalId,
            payoutAmount = amount,
            merchantAccount = merchantAccount,
            platformCashAccount = platformCashAccount
        )
        
        assertEquals(1, entries.size)
        val entry = entries.first()
        
        assertBalanced(entry)
        assertEquals(JournalType.PAYOUT, entry.journalType)
        assertPostingContains(entry, merchantAccount, isDebit = true, expectedAmount = 9_800)
        assertPostingContains(entry, platformCashAccount, isDebit = false, expectedAmount = 9_800)
    }
}
