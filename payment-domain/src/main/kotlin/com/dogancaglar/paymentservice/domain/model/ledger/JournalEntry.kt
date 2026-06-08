package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.payment.PaymentSplit
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId

/**
 * JournalEntry
 *
 * Represents one atomic, balanced double-entry accounting event.
 * Every JournalEntry is immutable and validates itself at construction time.
 *
 * Invariants enforced at construction:
 *  - Must have at least 2 postings (one debit, one credit minimum).
 *  - Total debit quantity must equal total credit quantity (balanced entry).
 *  - No duplicate account codes within a single entry.
 *
 * All instances must be created via the factory methods in [JournalFactory].
 * Direct construction is prohibited (private constructor).
 *
 * ============================================================
 * CAPTURE JOURNAL DESIGN (US 1.4)
 * ============================================================
 *
 * The old [capture] factory method has been renamed to [captureGrossAsset].
 * This rename is intentional and carries semantic weight:
 *
 *  - captureGrossAsset:
 *      Allocates 100% of the captured amount to the PRIMARY MERCHANT'S
 *      MERCHANT_GROSS_POOL account, regardless of whether the payment is
 *      DIRECT_MERCHANT or MARKETPLACE. The split distribution happens AFTER
 *      this entry, in separate INTERNAL_TRANSFER postings.
 *
 *  - executeSubSellerSplit:
 *      Generates one [JournalType.INTERNAL_TRANSFER] JournalEntry per split
 *      instruction. Each entry debits the merchant's MERCHANT_GROSS_POOL and
 *      credits the sub-seller's BalanceAccountType-designated account.
 *      This method is ONLY called for MARKETPLACE payments.
 *      The caller (PspResultConsumer) is responsible for checking processingModel
 *      before invoking this method.
 *
 * Why this two-step design?
 *  - It preserves a complete, auditable record: the gross settlement asset
 *    always lands in MERCHANT_GROSS_POOL first, making it trivial to reconcile
 *    captures against acquirer settlement batches without complicating the
 *    split accounting.
 *  - INTERNAL_TRANSFER entries are clearly labelled and isolated from the
 *    gateway-facing CAPTURE entry, so the ledger is human-readable.
 *  - If split distribution fails partway through, only the failed transfers
 *    are missing; the gross capture is never corrupted.
 */
class JournalEntry private constructor(
    val id: String,
    val journalType: JournalType,
    val name: String,
    val paymentId: PaymentId, // 🛡️ Strict Domain Primitive
    val txId: TxId,           // 🛡️ Strict Domain Primitive
    val postings: List<Posting>
) {

    init {
        require(postings.size >= 2) {
            "JournalEntry must have at least 2 postings, but had ${postings.size}"
        }
        val totalDebit  = postings.filterIsInstance<Posting.Debit>().sumOf { it.amount.quantity }
        val totalCredit = postings.filterIsInstance<Posting.Credit>().sumOf { it.amount.quantity }
        require(totalDebit == totalCredit) {
            "Unbalanced JournalEntry [$id]: debits=$totalDebit, credits=$totalCredit"
        }
        val duplicates = postings
            .groupingBy { it.account.accountCode }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicates.isEmpty()) {
            "JournalEntry [$id] contains duplicate accounts: ${duplicates.joinToString(", ")}"
        }
    }

    override fun toString(): String =
        "JournalEntry(id='$id', txType=$journalType, name='$name', " +
        "postings=$postings)"

    companion object JournalFactory {

        // =====================================================================
        // AUTHORIZATION  is succesful in sync api call
        // =====================================================================

        fun authHold(
            paymentId: PaymentId,
            txId: TxId, // <-- Added! (This is the ID of the AuthorizationTx)
            journalIdentifier: String,
            authorizedAmount: Amount,
            authReceivable: Account,
            authLiability: Account
        ): List<JournalEntry> = listOf(
            JournalEntry(
                id        = "AUTH:$journalIdentifier",
                journalType    = JournalType.AUTHORIZATION,
                name      = "AuthorizationTx Hold",
                paymentId = paymentId,
                txId      = txId,
                postings  = listOf(
                    Posting.Debit.create(authReceivable, authorizedAmount),
                    Posting.Credit.create(authLiability, authorizedAmount)
                )
            )
        )


        // =====================================================================
        // CAPTURE — This journal entry is recorded when external PSP notifies Mor-DC platform regarding the final status of capture, money is still not in Mor-DC account, but PSP confrms that it will send within 3 or 5 days
        // =====================================================================
        fun captureGrossAsset(
            paymentId: PaymentId, // <-- Added!
            txId: TxId,           // (This is the ID of the CaptureTx)
            journalIdentifier: String,
            capturedAmount: Amount,
            authReceivable: Account,
            authLiability: Account,
            merchantGrossPool: Account,
            pspReceivable: Account
        ): List<JournalEntry> = listOf(
            JournalEntry(
                id        = "CAPTURE:$journalIdentifier",
                journalType    = JournalType.CAPTURE,
                name      = "Gross Asset CaptureTx — Primary Merchant Pool",
                paymentId = paymentId,
                txId      = txId,
                postings  = listOf(
                    Posting.Debit.create(authLiability, capturedAmount),
                    Posting.Credit.create(authReceivable, capturedAmount),
                    Posting.Debit.create(pspReceivable, capturedAmount),
                    Posting.Credit.create(merchantGrossPool, capturedAmount)
                )
            )
        )

        // =====================================================================
        // INTERNAL_TRANSFER — Sub-Seller Split Distribution
        // =====================================================================



        /**
         * internalTransfer
         *
         * Generates a single INTERNAL_TRANSFER JournalEntry that redistributes
         * funds from the primary merchant's MERCHANT_GROSS_POOL to a target account.
         * Used by decoupled InternalTransferRequestExecutor processing single split requests.
         */
        fun internalTransfer(
            paymentId: PaymentId,
            txId: TxId,
            journalIdentifier: String,
            amount: Amount,
            merchantGrossPool: Account,
            targetAccount: Account,
            targetAccountType: AccountType,
            targetEntityId: String
        ): List<JournalEntry> = listOf(
            JournalEntry(
                id        = "INTERNAL_TRANSFER:${journalIdentifier}",
                journalType    = JournalType.INTERNAL_TRANSFER,
                name      = "Sub-Seller Split — $targetAccountType / $targetEntityId",
                paymentId = paymentId,
                txId      = txId,
                postings  = listOf(
                    Posting.Debit.create(merchantGrossPool, amount),
                    Posting.Credit.create(targetAccount, amount)
                )
            )
        )

        // =====================================================================
        // REFUND
        // =====================================================================

        /**
         * refund
         *
         * Reverses a prior capture by crediting the PSP with the refunded amount
         * and debiting the merchant's gross pool.
         *
         * Postings:
         *   DR MERCHANT_GROSS_POOL (reduces the merchant's balance)
         *   CR PSP_RECEIVABLE      (records the outbound refund to PSP)
         *   DR AUTH_LIABILITY      (re-opens the liability position)
         *   CR AUTH_RECEIVABLE     (reduces the receivable by refund amount)
         *
         * Called by: PspResultConsumer, processing a PaymentRefunded webhook event.
         */
        fun refund(
            paymentId: PaymentId, // <-- Added!
            txId: TxId,
            journalIdentifier: String,
            refundedAmount: Amount,
            authReceivable: Account,
            authLiability: Account,
            merchantGrossPool: Account,
            pspReceivable: Account
        ): List<JournalEntry> = listOf(
            JournalEntry(
                id            = "REFUND:$journalIdentifier",
                journalType        = JournalType.REFUND,
                name          = "Payment RefundTx",
                paymentId = paymentId,
                txId= txId,
                postings      = listOf(
                    Posting.Debit.create(authLiability, refundedAmount),
                    Posting.Credit.create(authReceivable, refundedAmount),
                    Posting.Debit.create(merchantGrossPool, refundedAmount),
                    Posting.Credit.create(pspReceivable, refundedAmount)
                )
            )
        )

        // =====================================================================
        // SETTLEMENT
        // =====================================================================

        /**
         * settlement
         *
         * Records funds received from the acquirer into the platform's cash account.
         * The difference between capturedAmount and settledAmount represents PSP fees.
         *
         * Postings:
         *   DR PSP_FEE_EXPENSE   (PSP fee absorbed by platform)
         *   DR PLATFORM_CASH     (net funds received)
         *   CR PSP_RECEIVABLE    (clears the receivable)
         */
        fun settlement(
            paymentId: PaymentId,
            txId: TxId,
            journalIdentifier: String,
            capturedAmount: Amount,
            settledAmount: Amount,
            platformCashAccount: Account,
            pspFeeExpenseAccount: Account,
            pspReceivable: Account
        ): List<JournalEntry> = listOf(
            JournalEntry(
                id            = "SETTLEMENT:$journalIdentifier",
                journalType        = JournalType.SETTLEMENT,
                name          = "Acquirer Funds Settlement",
                paymentId     = paymentId,
                txId          = txId,
                postings      = listOf(
                    Posting.Debit.create(pspFeeExpenseAccount, capturedAmount - settledAmount),
                    Posting.Debit.create(platformCashAccount, settledAmount),
                    Posting.Credit.create(pspReceivable, capturedAmount)
                )
            )
        )

        // =====================================================================
        // COMMISSION FEE
        // =====================================================================

        /**
         * commissionFeeRegistered
         *
         * Records the platform's commission charge against the merchant account.
         *
         * Postings:
         *   DR MERCHANT_GROSS_POOL         (reduces merchant balance by commission)
         *   CR PLATFORM_COMMISSION_ESCROW  (records the commission receivable)
         */
        fun commissionFeeRegistered(
            paymentId: PaymentId,
            txId: TxId,
            journalIdentifier: String,
            commissionFee: Amount,
            commissionFeeAccount: Account,
            merchantAccount: Account
        ): List<JournalEntry> = listOf(
            JournalEntry(
                id       = "COMMISSION_FEE:$journalIdentifier",
                journalType   = JournalType.COMMISSION_FEE,
                name     = "Platform Commission Fee",
                paymentId = paymentId,
                txId     = txId,
                postings = listOf(
                    Posting.Debit.create(merchantAccount, commissionFee),
                    Posting.Credit.create(commissionFeeAccount, commissionFee)
                )
            )
        )

        // =====================================================================
        // PAYOUT
        // =====================================================================

        /**
         * payout
         *
         * Records a cash disbursement from the platform to a merchant/seller.
         *
         * Postings:
         *   DR MERCHANT_GROSS_POOL  (reduces the merchant's payable balance)
         *   CR PLATFORM_CASH        (reduces platform's cash holding)
         */
        fun payout(
            paymentId: PaymentId,
            txId: TxId,
            journalIdentifier: String,
            payoutAmount: Amount,
            merchantAccount: Account,
            platformCashAccount: Account
        ): List<JournalEntry> = listOf(
            JournalEntry(
                id       = "PAYOUT:$journalIdentifier",
                journalType   = JournalType.PAYOUT,
                name     = "Merchant Payout Disbursement",
                paymentId = paymentId,
                txId     = txId,
                postings = listOf(
                    Posting.Debit.create(merchantAccount, payoutAmount),
                    Posting.Credit.create(platformCashAccount, payoutAmount)
                )
            )
        )

        // =====================================================================
        // PERSISTENCE REHYDRATION
        // =====================================================================

        /**
         * fromPersistence
         *
         * Rehydrates a JournalEntry from persisted database rows.
         * Used exclusively by the repository layer. No business validation runs here
         * beyond the init{} block invariants — assume the DB holds valid, balanced data.
         */
        fun rehytrate(
            id: String,
            txType: JournalType,
            name: String,
            paymentId: PaymentId,
            txId: TxId,
            postings: List<Posting>
        ): JournalEntry = JournalEntry(
            id            = id,
            journalType        = txType,
            name          = name,
            paymentId     = paymentId,
            txId          = txId,
            postings      = postings
        )
    }
}