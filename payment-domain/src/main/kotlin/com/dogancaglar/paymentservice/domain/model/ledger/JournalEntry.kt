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
        val totalDebitAmount = postings.filterIsInstance<Posting.Debit>().sumOf { it.amount.quantity }
        val totalCreditAmount = postings.filterIsInstance<Posting.Credit>().sumOf { it.amount.quantity }
        require(totalDebitAmount == totalCreditAmount) {
            "Unbalanced journal entry: debits and credits differ (debits=$totalDebitAmount, credits=$totalCreditAmount)"
        }
    }

    companion object JournalFactory {

        // ==================== Factory Methods ====================

        fun authHold(paymentOrderId: String, authorizedAmount: Amount): List<JournalEntry> =
            listOf(JournalEntry(
                id = "AUTH:$paymentOrderId",
                txType = JournalType.AUTH_HOLD,
                name = "Authorization Hold",
                postings = listOf(
                    Posting.Debit.create(Account.create(AccountType.AUTH_RECEIVABLE), authorizedAmount),
                    Posting.Credit.create(Account.create(AccountType.AUTH_LIABILITY), authorizedAmount)
                )
            )
            )

        fun capture(paymentOrderId: String, capturedAmount: Amount, merchantAccount: Account): List<JournalEntry> =
            listOf(JournalEntry(
                id = "CAPTURE:$paymentOrderId",
                txType = JournalType.CAPTURE,
                name = "Payment Capture",
                postings = listOf(
                    Posting.Credit.create(Account.create(AccountType.AUTH_RECEIVABLE), capturedAmount),
                    Posting.Debit.create(Account.create(AccountType.AUTH_LIABILITY), capturedAmount),
                    Posting.Credit.create(merchantAccount, capturedAmount),
                    Posting.Debit.create(Account.create(AccountType.PSP_RECEIVABLES), capturedAmount)
                )
            ))

        fun authHoldAndCapture(
            paymentOrderId: String,
            capturedAmount: Amount,
            merchantAccount: Account
        ): List<JournalEntry> {
            val authJournalEntry = authHold(paymentOrderId, capturedAmount);
            val captureJournalEntry = capture(paymentOrderId, capturedAmount, merchantAccount)
            return authJournalEntry + captureJournalEntry;
        }

        fun settlement(
            paymentOrderId: String,
            grossAmount: Amount,
            interchangeFee: Amount,
            schemeFee: Amount,
            acquirerAccount: Account
        ): List<JournalEntry> =
            listOf(JournalEntry(
                id = "SETTLEMENT:$paymentOrderId",
                txType = JournalType.SETTLEMENT,
                name = "Funds received from Acquirer",
                postings = listOf(
                    Posting.Debit.create(Account.create(AccountType.SCHEME_FEES), schemeFee),
                    Posting.Debit.create(Account.create(AccountType.INTERCHANGE_FEES), interchangeFee),
                    Posting.Debit.create(
                        acquirerAccount,
                        Amount.of(
                            grossAmount.quantity - (schemeFee.quantity + interchangeFee.quantity),
                            grossAmount.currency
                        )
                    ),
                    Posting.Credit.create(Account.create(AccountType.PSP_RECEIVABLES), grossAmount)
                )
            )
            )

        fun feeRegistered(paymentOrderId: String, pspFee: Amount, merchantAccount: Account): List<JournalEntry> =
            listOf(JournalEntry(
                id = "PSP-FEE:$paymentOrderId",
                txType = JournalType.FEE,
                name = "Psp Fee is recorded",
                postings = listOf(
                    Posting.Debit.create(merchantAccount, pspFee),
                    Posting.Credit.create(Account.create(AccountType.PROCESSING_FEE_REVENUE), pspFee)
                )
            ))

        fun payout(
            paymentOrderId: String,
            payoutAmount: Amount,
            merchantAccount: Account,
            acquirerAccount: Account
        ): List<JournalEntry> =
            listOf(JournalEntry(
                id = "PAYOUT:$paymentOrderId",
                txType = JournalType.PAYOUT,
                name = "Merchant Payout",
                postings = listOf(
                    Posting.Debit.create(merchantAccount, payoutAmount),
                    Posting.Credit.create(acquirerAccount, payoutAmount)
                )
            ))

        // ==================== Convenience Methods ====================

        fun fullFlow(
            paymentOrderId: String,
            amount: Amount,
            merchantAccount: Account,
            acquirerAccount: Account
        ): List<JournalEntry> {
            val pspFee = Amount.of(200, amount.currency)

            val authEntry = authHold(paymentOrderId, amount)
            val captureEntry = capture(paymentOrderId, amount, merchantAccount)

            val settlementEntry = settlement(
                paymentOrderId,
                amount,
                Amount.of(0, amount.currency),
                Amount.of(0, amount.currency),
                acquirerAccount
            )

            val feeEntry = feeRegistered(paymentOrderId, pspFee, merchantAccount)
            val payoutEntry = payout(
                paymentOrderId,
                Amount.of(amount.quantity - pspFee.quantity, amount.currency),
                merchantAccount,
                acquirerAccount
            )

            return authEntry + captureEntry + settlementEntry + feeEntry + payoutEntry
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