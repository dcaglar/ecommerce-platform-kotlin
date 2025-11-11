package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.Amount

/**
 * Represents one atomic, balanced accounting event.
 * Each JournalEntry must sum to zero across all postings.
 *
 * Use the static factory methods in the companion object to create validated JournalEntry instances.
 * Example: JournalEntry.authHold("PAY-1", amount)
 */
class JournalEntry private constructor(
    val id: String,                      // e.g. "CAPTURE:A999"
    val txType: JournalType,             // e.g. AUTH_HOLD, CAPTURE, REFUND
    val name: String,                    // Human-readable label (optional)
    val postings: List<Posting>,
    val referenceType: String? = null,   // e.g. "authorization", "capture"
    val referenceId: String? = null      // e.g. "A999"
) {

    init {
        require(postings.isNotEmpty() && postings.size>=2) { "JournalEntry must have at least 2 posting" }
        val totalDebitAmount = postings.filterIsInstance<Posting.Debit>().sumOf { it.amount.quantity }
        val totalCreditAmount = postings.filterIsInstance<Posting.Credit>().sumOf { it.amount.quantity }
        require(totalDebitAmount == totalCreditAmount) {
            "Unbalanced journal entry: debits and credits differ (debits=$totalDebitAmount, credits=$totalCreditAmount)"
        }

        val duplicateAccounts = postings
            .groupingBy { it.account.accountCode }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        require(duplicateAccounts.isEmpty()) {
            "JournalEntry contains duplicate accounts: ${duplicateAccounts.joinToString(", ")}"
        }
    }

    companion object JournalFactory {

        // ==================== Factory Methods ====================

        fun authHold(journalIdentifier:String,
                     authorizedAmount: Amount,
                     authReceivable:Account,
                     authLiability:Account): List<JournalEntry> =
            listOf(JournalEntry(
                id = "AUTH:${journalIdentifier}",
                txType = JournalType.AUTH_HOLD,
                name = "Authorization Hold",
                postings = listOf(
                    Posting.Debit.create(authReceivable, authorizedAmount),
                    Posting.Credit.create(authLiability, authorizedAmount)
                )
            )
            )

        fun capture(journalIdentifier:String,capturedAmount: Amount,
                    authReceivable:Account,
                    authLiability:Account,
                    merchantAccount: Account,
                    pspReceivable : Account): List<JournalEntry> =
            listOf(JournalEntry(
                id = "CAPTURE:${journalIdentifier}",
                txType = JournalType.CAPTURE,
                name = "Payment Capture",
                postings = listOf(
                    Posting.Credit.create(authReceivable, capturedAmount),
                    Posting.Debit.create(authLiability,capturedAmount),
                    Posting.Credit.create(merchantAccount, capturedAmount),
                    Posting.Debit.create(pspReceivable, capturedAmount)
                )
            ))


        fun settlement(
            journalIdentifier:String,
            capturedAmount: Amount,
            settledAmount: Amount,
            platformCashAccount: Account,
            pspFeeExpenseAccount: Account,
            pspReceivable: Account
        ): List<JournalEntry> =
            listOf(JournalEntry(
                id = "SETTLEMENT:${journalIdentifier}",
                txType = JournalType.SETTLEMENT,
                name = "Funds received from Acquirer",
                postings = listOf(
                    Posting.Debit.create(pspFeeExpenseAccount,capturedAmount - settledAmount),
                    Posting.Debit.create(platformCashAccount, settledAmount),
                    Posting.Credit.create(pspReceivable, capturedAmount)
                )
            )
            )

        fun commissionFeRegistered(journalIdentifier:String, commissionFee: Amount, commissiissionFeeAccount:Account ,merchantAccount: Account): List<JournalEntry> =
            listOf(JournalEntry(
                id = "PSP-FEE:$journalIdentifier",
                txType = JournalType.COMMISSION_FEE,
                name = "Psp Fee is recorded",
                postings = listOf(
                    Posting.Debit.create(merchantAccount, commissionFee),
                    Posting.Credit.create(commissiissionFeeAccount, commissionFee)
                )
            ))

        fun payout(
            journalIdentifier: String,
            payoutAmount: Amount,
            merchantAccount: Account,
            platformCashAccount: Account
        ): List<JournalEntry> =
            listOf(JournalEntry(
                id = "PAYOUT:$journalIdentifier",
                txType = JournalType.PAYOUT,
                name = "Merchant Payout",
                postings = listOf(
                    Posting.Debit.create(merchantAccount, payoutAmount),
                    Posting.Credit.create(platformCashAccount, payoutAmount)
                )
            ))

        // ==================== Convenience Methods ====================

        fun fullFlow(
            paymentOrderId: String,
            amount: Amount,
            authReceivable:Account,
            authLiability:Account,
            pspReceivable: Account,
            merchantAccount: Account,
            acquirerAccount: Account
        ): List<JournalEntry> {
           return emptyList()
        }

        fun failedPayment(paymentOrderId: String, amount: Amount): List<JournalEntry> =
            emptyList()

        fun fromPersistence(
            id: String,
            txType: JournalType,
            name: String,
            postings: List<Posting>,
            referenceType: String? = null,
            referenceId: String? = null
        ): JournalEntry = JournalEntry(
            id = id,
            txType = txType,
            name = name,
            postings = postings,
            referenceType = referenceType,
            referenceId = referenceId
        )

    }


}