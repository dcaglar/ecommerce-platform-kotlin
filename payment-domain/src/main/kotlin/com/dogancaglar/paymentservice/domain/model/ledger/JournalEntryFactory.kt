package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.Amount
import java.util.UUID

object JournalEntryFactory {

    fun authHold(paymentId: String, txAmount: Amount): JournalEntry =
        JournalEntry(
            id = "AUTH:$paymentId",
            txType = JournalType.AUTH_HOLD,
            name = "Authorization Hold",
            postings = listOf(
                Posting.Debit(Account(accountType = AccountType.AUTH_RECEIVABLE),txAmount),
                Posting.Credit( Account(accountType = AccountType.AUTH_LIABILITY),txAmount)
            )
        )

    fun capture(paymentId: String, capturedAmount: Amount,merchantAccount: Account): JournalEntry =
        JournalEntry(
            id = "CAPTURE:$paymentId",
            txType = JournalType.CAPTURE,
            name = "Payment Capture",
            postings = listOf(
                Posting.Credit(Account(accountType = AccountType.AUTH_RECEIVABLE),capturedAmount),
                Posting.Debit( Account(accountType = AccountType.AUTH_LIABILITY),capturedAmount),
                Posting.Credit(merchantAccount,capturedAmount) ,
                Posting.Debit(Account(accountType = AccountType.PSP_RECEIVABLES),capturedAmount )
            )
        )





    //bank/scheme pays us on our acquirer account (grossAmount-(schemefee+interchange-fee)),clear psp_receivable,received
    fun settlement(paymentId: String, grossAmount: Amount, interchangeFee: Amount,schemeFee: Amount,acquirerAccount: Account): JournalEntry =
        JournalEntry(
            id = "SETTLEMENT:$paymentId",
            txType = JournalType.SETTLEMENT,
            name = "Funds received from Acquirer",
            postings = listOf(
                Posting.Debit(Account(accountType = AccountType.SCHEME_FEES),schemeFee),
                Posting.Debit(Account(accountType = AccountType.INTERCHANGE_FEES),interchangeFee),
                Posting.Debit(acquirerAccount,Amount(grossAmount.value-(schemeFee.value+interchangeFee.value),grossAmount.currency)) ,
                Posting.Credit(Account(accountType = AccountType.PSP_RECEIVABLES),grossAmount),
            )
        )

    //fee registered before payout,reduce merchant payable,record it in processing as fee(expense)
    fun feeRegistered(paymentId: String,pspFee: Amount,merchantAccount: Account): JournalEntry =
        JournalEntry(
            id = "PSP-FEE:$paymentId",
            txType = JournalType.FEE,
            name = "Psp Fee is recorded",//reduce merchant liability, and also reduce acquirer account()
            postings = listOf(
                Posting.Debit(merchantAccount,pspFee),
                Posting.Credit(Account(accountType = AccountType.PROCESSING_FEE_REVENUE),pspFee)
            )
        )
    fun payout(paymentId: String, payoutAmount: Amount,merchantAccount: Account,acquirerAccount: Account): JournalEntry =
        JournalEntry(
            id = "PAYOUT:$paymentId",
            txType = JournalType.PAYOUT,
            name = "Merchant Payout",
            postings = listOf(
                Posting.Debit(merchantAccount,payoutAmount),
                Posting.Credit(acquirerAccount, payoutAmount)
            )
        )
}