package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.Amount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedgerDomainTest {
    val acquirerAccount = Account(accountId = "merchantId", accountType = AccountType.ACQUIRER_ACCOUNT)
    val merchantAccount = Account(accountId = "merchantId", accountType = AccountType.MERCHANT_ACCOUNT)
    val pspFeeRevenueAccount = Account( accountType = AccountType.PROCESSING_FEE_REVENUE)
    val authLiabilityAccount = Account( accountType = AccountType.AUTH_LIABILITY)
    val authReceiableAccount = Account( accountType = AccountType.AUTH_RECEIVABLE)
    val pspReceivableAccount = Account( accountType = AccountType.PSP_RECEIVABLES)

    @Test
    fun `auth hold should balance and use correct accounts`() {
        val amount = Amount(10_000, "EUR")
        val entry = JournalEntryFactory.authHold("PAY-1", amount)
        val totalDebitAmount = entry.postings.filterIsInstance<Posting.Debit>().sumOf { it.amount.value }
        val totalCreditAmount = entry.postings.filterIsInstance<Posting.Credit>().sumOf { it.amount.value }
        val netBalanceByAccount = entry.postings
            .groupBy{it.account.getAccountCode()}
            .mapValues {(accCode,postings) -> postings.sumOf { it.getSignedAmount().value } }
        assertTrue(entry.postings.isNotEmpty())
        assertEquals(totalDebitAmount, totalCreditAmount)
        assertTrue(entry.postings.any { it.account.accountType == AccountType.AUTH_RECEIVABLE })
        assertTrue(entry.postings.any { it.account.accountType == AccountType.AUTH_LIABILITY })
        assertEquals(10_000,netBalanceByAccount.get(authLiabilityAccount.getAccountCode()))
        assertEquals(10_000,netBalanceByAccount.get(authReceiableAccount.getAccountCode()))

    }

    @Test
    fun `capture should balance and shift from auth to psp receivables,and increase merchant payable`() {
        val amount = Amount(10_000, "EUR")
        val entry = JournalEntryFactory.capture("PAY-2", amount, merchantAccount)
        val drAccounts = entry.postings.filterIsInstance<Posting.Debit>().map { it.account.accountType }
        val crAccounts = entry.postings.filterIsInstance<Posting.Credit>().map { it.account.accountType }
        assertTrue(entry.postings.isNotEmpty())
        assertTrue(AccountType.PSP_RECEIVABLES in drAccounts)
        assertTrue(AccountType.AUTH_RECEIVABLE in crAccounts)
        assertTrue(AccountType.AUTH_LIABILITY in drAccounts)
        assertTrue(AccountType.MERCHANT_ACCOUNT in crAccounts)
        val netAmountByAccount = entry.postings
            .groupBy { it.account.getAccountCode() }
            .mapValues { (accCode:String,postings:List<Posting>) -> postings.sumOf { it.getSignedAmount().value  } }
         //global balance should be zero
        assertEquals(0L, entry.postings.sumOf { it.getSignedAmount().value })
        //merchant payable up
        assertEquals(10000,netAmountByAccount[merchantAccount.getAccountCode()])
        //pspReceiable up
        assertEquals(10000,netAmountByAccount[pspReceivableAccount.getAccountCode()])
        //balance out auths
        assertEquals(-10_000,netAmountByAccount.get(authLiabilityAccount.getAccountCode()))
        assertEquals(-10_000,netAmountByAccount.get(authReceiableAccount.getAccountCode()))
    }

    @Test
    fun `settlement should record acquirer inflow and fee expenses`() {
        val gross = Amount(10_000, "EUR")
        val interchange = Amount(300, "EUR")
        val scheme = Amount(200, "EUR")
        val entry = JournalEntryFactory.settlement("PAY-3", gross, interchange, scheme,acquirerAccount)

        assertEquals(0L, entry.postings.sumOf { it.getSignedAmount().value })
        assertTrue(entry.postings.any { it.account.accountType == AccountType.ACQUIRER_ACCOUNT })
        assertTrue(entry.postings.any { it.account.accountType == AccountType.PSP_RECEIVABLES })
        assertTrue(entry.postings.any { it.account.accountType == AccountType.SCHEME_FEES })
        assertTrue(entry.postings.any { it.account.accountType == AccountType.INTERCHANGE_FEES })
    }

    @Test
    fun `feeRegistered should reduce merchant liability and record PSP revenue`() {
        val fee = Amount(200, "EUR")
        val entry = JournalEntryFactory.feeRegistered("PAY-4", fee,merchantAccount)

        assertEquals(0L, entry.postings.sumOf { it.getSignedAmount().value })
        val debit = entry.postings.filterIsInstance<Posting.Debit>().first()
        val credit = entry.postings.filterIsInstance<Posting.Credit>().first()

        assertEquals(merchantAccount.accountType, debit.account.accountType)
        assertEquals(AccountType.PROCESSING_FEE_REVENUE, credit.account.accountType)
    }

    @Test
    fun `payout should decrease acquirer cash and merchant payable`() {

        val payout = Amount(9_800, "EUR")
        val entry = JournalEntryFactory.payout("MERCHANT-1", payout, merchantAccount, acquirerAccount)
        val netByAccount = entry.postings
            .groupBy { it.account.getAccountCode() }
            .mapValues { (_, posts) -> posts.sumOf { it.getSignedAmount().value } }

        assertEquals(-9800,netByAccount[merchantAccount.getAccountCode()])
        assertEquals(-9800,netByAccount[acquirerAccount.getAccountCode()])

        assertTrue(entry.postings.any { it.account.getAccountCode()==merchantAccount.getAccountCode()})
        assertTrue(entry.postings.any {  it.account.getAccountCode()==acquirerAccount.getAccountCode() })
    }

    @Test
    fun `full flow should net PSP fee as profit and merchant payable cleared`() {
        val pspFeeRevenueAccount = Account( accountType = AccountType.PROCESSING_FEE_REVENUE)
        val capture = Amount(10_000, "EUR")
        val settlement = Amount(10_000, "EUR")
        val fee = Amount(200, "EUR")
        val payout = Amount(9_800, "EUR")

        val entries = listOf(
            JournalEntryFactory.capture("PAY-5", capture, merchantAccount),
            JournalEntryFactory.settlement("PAY-5", settlement, Amount(0, "EUR"), Amount(0, "EUR"),acquirerAccount),
            JournalEntryFactory.feeRegistered("PAY-5", fee,merchantAccount),
            JournalEntryFactory.payout("MERCHANT-1", payout, merchantAccount, acquirerAccount)
        )

        val allPostings = entries.flatMap { it.postings }

        val totalDebits = allPostings.filterIsInstance<Posting.Debit>().sumOf { it.amount.value }
        val totalCredits = allPostings.filterIsInstance<Posting.Credit>().sumOf { it.amount.value }
        //generate balances per account
        val netByAccount = allPostings
            .groupBy { it.account.getAccountCode() }
            .mapValues { (_, posts) -> posts.sumOf { it.getSignedAmount().value } }


        assertEquals(totalDebits, totalCredits, "Global double-entry check failed")
        // PSP keeps fee in acquirer account and recognizes revenue
        assertEquals(200L, netByAccount[acquirerAccount.getAccountCode()])
        assertEquals(200L, netByAccount[pspFeeRevenueAccount.getAccountCode()])
    }
}