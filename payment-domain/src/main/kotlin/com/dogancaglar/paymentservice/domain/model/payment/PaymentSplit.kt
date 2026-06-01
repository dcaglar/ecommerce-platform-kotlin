package com.dogancaglar.paymentservice.domain.model.payment

import com.dogancaglar.paymentservice.domain.model.common.Amount

/**
 * PaymentSplit
 *
 * An immutable value object that encodes a single fund-routing instruction
 * within a payment. A collection of these is locked into the Payment aggregate
 * at authorization time and read back during the capture webhook phase to
 * generate INTERNAL_TRANSFER ledger postings.
 *
 * Invariants enforced at construction time:
 *  - targetEntityId must be non-blank (identifies the seller, sub-merchant,
 *    or platform entity that is the beneficiary of this split).
 *  - amount must be positive.
 *  - targetAccountType must be one of the canonical BalanceAccountType values.
 *
 * No cart items, product lines, or order-level concepts exist here.
 * This is a pure fintech routing primitive.
 *
 * @param targetAccountType  The internal ledger bucket that receives funds.
 * @param targetEntityId     Identifier of the owning entity (seller ID, merchant ID, etc.).
 * @param amount             The monetary amount to route to this account.
 */
data class PaymentSplit(
    val targetAccountType: BalanceAccountType,
    val targetEntityId: String,
    val amount: Amount
) {
    init {
        require(targetEntityId.isNotBlank()) {
            "PaymentSplit.targetEntityId must not be blank"
        }
        require(amount.isPositive()) {
            "PaymentSplit.amount must be positive, but was ${amount.quantity}"
        }
    }

    companion object {
        /**
         * Factory method providing explicit, named-argument construction
         * and a validation gate for incoming split instructions.
         */
        fun of(
            targetAccountType: BalanceAccountType,
            targetEntityId: String,
            amount: Amount
        ): PaymentSplit = PaymentSplit(
            targetAccountType = targetAccountType,
            targetEntityId = targetEntityId,
            amount = amount
        )
    }
}
