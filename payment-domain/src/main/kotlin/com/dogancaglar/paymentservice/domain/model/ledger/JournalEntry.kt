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
        require(postings.isNotEmpty()) { "JournalEntry must have at least one posting" }
        val totalDebitAmount = postings.filterIsInstance<Posting.Debit>().sumOf { it.amount.value }
        val totalCreditAmount = postings.filterIsInstance<Posting.Credit>().sumOf { it.amount.value }
        require(totalDebitAmount == totalCreditAmount) {
            "Unbalanced journal entry: debits and credits differ (debits=$totalDebitAmount, credits=$totalCreditAmount)"
        }
    }

    companion object {
        
        // ==================== Factory Methods ====================
        
        fun authHold(paymentId: String, txAmount: Amount): JournalEntry =
            JournalEntry(
                id = "AUTH:$paymentId",
                txType = JournalType.AUTH_HOLD,
                name = "Authorization Hold",
                postings = listOf(
                    Posting.Debit(Account(accountType = AccountType.AUTH_RECEIVABLE), txAmount),
                    Posting.Credit(Account(accountType = AccountType.AUTH_LIABILITY), txAmount)
                )
            )

        fun capture(paymentId: String, capturedAmount: Amount, merchantAccount: Account): JournalEntry =
            JournalEntry(
                id = "CAPTURE:$paymentId",
                txType = JournalType.CAPTURE,
                name = "Payment Capture",
                postings = listOf(
                    Posting.Credit(Account(accountType = AccountType.AUTH_RECEIVABLE), capturedAmount),
                    Posting.Debit(Account(accountType = AccountType.AUTH_LIABILITY), capturedAmount),
                    Posting.Credit(merchantAccount, capturedAmount),
                    Posting.Debit(Account(accountType = AccountType.PSP_RECEIVABLES), capturedAmount)
                )
            )

        fun settlement(
            paymentId: String,
            grossAmount: Amount,
            interchangeFee: Amount,
            schemeFee: Amount,
            acquirerAccount: Account
        ): JournalEntry =
            JournalEntry(
                id = "SETTLEMENT:$paymentId",
                txType = JournalType.SETTLEMENT,
                name = "Funds received from Acquirer",
                postings = listOf(
                    Posting.Debit(Account(accountType = AccountType.SCHEME_FEES), schemeFee),
                    Posting.Debit(Account(accountType = AccountType.INTERCHANGE_FEES), interchangeFee),
                    Posting.Debit(acquirerAccount, Amount(grossAmount.value - (schemeFee.value + interchangeFee.value), grossAmount.currency)),
                    Posting.Credit(Account(accountType = AccountType.PSP_RECEIVABLES), grossAmount)
                )
            )

        fun feeRegistered(paymentId: String, pspFee: Amount, merchantAccount: Account): JournalEntry =
            JournalEntry(
                id = "PSP-FEE:$paymentId",
                txType = JournalType.FEE,
                name = "Psp Fee is recorded",
                postings = listOf(
                    Posting.Debit(merchantAccount, pspFee),
                    Posting.Credit(Account(accountType = AccountType.PROCESSING_FEE_REVENUE), pspFee)
                )
            )

        fun payout(paymentId: String, payoutAmount: Amount, merchantAccount: Account, acquirerAccount: Account): JournalEntry =
            JournalEntry(
                id = "PAYOUT:$paymentId",
                txType = JournalType.PAYOUT,
                name = "Merchant Payout",
                postings = listOf(
                    Posting.Debit(merchantAccount, payoutAmount),
                    Posting.Credit(acquirerAccount, payoutAmount)
                )
            )

        // ==================== Convenience Methods ====================

        fun fullFlow(paymentOrderId: String, amount: Amount, merchantAccount: Account, acquirerAccount: Account): List<JournalEntry> {
            val pspFee = Amount(200, amount.currency)

            val authEntry = authHold(paymentOrderId, amount)
            val captureEntry = capture(paymentOrderId, amount, merchantAccount)

            val settlementEntry = settlement(
                paymentOrderId,
                amount,
                Amount(0, amount.currency),
                Amount(0, amount.currency),
                acquirerAccount
            )

            val feeEntry = feeRegistered(paymentOrderId, pspFee, merchantAccount)
            val payoutEntry = payout(
                paymentOrderId,
                Amount(amount.value - pspFee.value, amount.currency),
                merchantAccount,
                acquirerAccount
            )

            return listOf(authEntry, captureEntry, settlementEntry, feeEntry, payoutEntry)
        }

        fun failedPayment(paymentOrderId: String, amount: Amount): List<JournalEntry> =
            emptyList()
        
        // Internal: Test-only method for creating custom entries
        // Visible to tests in payment-domain module
        internal fun createForTest(
            id: String,
            txType: JournalType,
            name: String = "Test Entry",
            postings: List<Posting>,
            referenceType: String? = null,
            referenceId: String? = null
        ): JournalEntry = JournalEntry(id, txType, name, postings, referenceType, referenceId)
    }
}