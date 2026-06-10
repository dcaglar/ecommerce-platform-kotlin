package com.dogancaglar.paymentservice.domain.model.payment

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.vo.InternalTransferId
import com.dogancaglar.paymentservice.domain.model.vo.TxId
import java.time.LocalDateTime

/**
 * InternalTransfer — Central DDD Aggregate Root
 *
 * The source of truth for the financial state machine of a single
 * internal fund movement within the Mor-DC platform. It enforces all
 * mathematical invariants for internal balance transfers and reversals.
 *
 * @param transferId          Snowflake-generated identifier for this Transfer record.
 * @param sourceTransactionId Reference to the originating transaction (e.g., CaptureTx) that caused this transfer.
 * @param amount              Total amount transferred. Immutable after creation.
 * @param reversedAmount      Running total of reversed funds. Starts at zero.
 * @param targetAccountType   The type of the destination account.
 * @param targetAccount      The specific entity ID of the destination account.
 * @param sourceAccountType   The type of the source account.
 * @param sourceAccount      The specific entity ID of the source account.
 * @param status              Current lifecycle state of this transfer.
 * @param createdAt           Timestamp of aggregate creation (UTC).
 * @param updatedAt           Timestamp of last state mutation (UTC).
 */
class InternalTransfer private constructor(
    val transferId: InternalTransferId,
    val sourceTransactionId: TxId,
    val amount: Amount,
    val reversedAmount: Amount,
    val targetAccount: String,
    val sourceAccount: String,
    val transferType: String,
    val status: InternalTransferStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {

    // =========================================================================
    // Aggregate Invariants (enforced on every construction path)
    // =========================================================================

    init {
        require(targetAccount.isNotBlank()) {
            "targetEntityId must not be blank"
        }
        require(sourceAccount.isNotBlank()) {
            "sourceEntityId must not be blank"
        }
        require(amount.isPositive()) {
            "amount must be positive, but was ${amount.quantity}"
        }
        require(reversedAmount >= Amount.zero(amount.currency)) {
            "reversedAmount cannot be negative, but was ${reversedAmount.quantity}"
        }
        require(reversedAmount <= amount) {
            "reversedAmount (${reversedAmount.quantity}) cannot exceed total amount (${amount.quantity})"
        }
        require(reversedAmount.currency == amount.currency) {
            "reversedAmount currency (${reversedAmount.currency}) must match amount currency (${amount.currency})"
        }
    }

    // =========================================================================
    // State Machine
    // =========================================================================


    // =========================================================================
    // Internal Immutable Copy
    // =========================================================================

    fun markSentForTransfer(now: LocalDateTime = Utc.nowLocalDateTime()): InternalTransfer {
        require(status == InternalTransferStatus.CREATED_PENDING) {
            "Can only mark SENT_FOR_TRANSFER from CREATED_PENDING (current=$status)"
        }
        return copy(status = InternalTransferStatus.SENT_FOR_TRANSFER, updatedAt = now)
    }

    fun markTransferred(now: LocalDateTime = Utc.nowLocalDateTime()): InternalTransfer {
        require(status == InternalTransferStatus.SENT_FOR_TRANSFER) {
            "Can only mark TRANSFERRED from SENT_FOR_TRANSFER (current=$status)"
        }
        return copy(status = InternalTransferStatus.TRANSFERRED, updatedAt = now)
    }

    private fun copy(
        status: InternalTransferStatus = this.status,
        updatedAt: LocalDateTime = Utc.nowLocalDateTime()
    ): InternalTransfer = InternalTransfer(
        transferId          = transferId,
        sourceTransactionId = sourceTransactionId,
        amount              = amount,
        reversedAmount      = reversedAmount,
        targetAccount      = targetAccount,
        sourceAccount      = sourceAccount,
        transferType        = transferType,
        status              = status,
        createdAt           = createdAt,
        updatedAt           = updatedAt
    )

    // =========================================================================
    // Display
    // =========================================================================

    override fun toString(): String =
        "InternalTransfer(transferId=${transferId.value}, sourceTransactionId=${sourceTransactionId.value}, " +
        "amount=$amount, reversedAmount=$reversedAmount, target=$targetAccount/$targetAccount, " +
        "source=$sourceAccount/$sourceAccount, status=$status, transferType= $transferType createdAt=$createdAt, updatedAt=$updatedAt)"

    // =========================================================================
    // Factory Methods
    // =========================================================================

    companion object {

        /**
         * initialize
         *
         * The canonical factory method for creating a new InternalTransfer aggregate.
         */
        fun createNew(
            transferId: InternalTransferId,
            sourceTransactionId: TxId,
            amount: Amount,
            sourceAccount: String,
            targetAccount: String,
            transferType :String,
            now: LocalDateTime = Utc.nowLocalDateTime()
        ): InternalTransfer {
            return InternalTransfer(
                transferId          = transferId,
                sourceTransactionId = sourceTransactionId,
                amount              = amount,
                reversedAmount      = Amount.zero(amount.currency),
                targetAccount      = targetAccount,
                sourceAccount      = sourceAccount,
                transferType = transferType,
                status              = InternalTransferStatus.CREATED_PENDING,
                createdAt           = now,
                updatedAt           = now
            )
        }

        /**
         * rehydrate
         *
         * Reconstructs an InternalTransfer aggregate from persisted database columns.
         */
        fun rehydrate(
            transferId: InternalTransferId,
            sourceTransactionId: TxId,
            amount: Amount,
            reversedAmount: Amount,
            targetAccount: String,
            sourceAccount: String,
            status: InternalTransferStatus,
            transferType : String,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): InternalTransfer = InternalTransfer(
            transferId          = transferId,
            sourceTransactionId = sourceTransactionId,
            amount              = amount,
            reversedAmount      = reversedAmount,
            targetAccount      = targetAccount,
            sourceAccount      = sourceAccount,
            transferType = transferType,
            status              = status,
            createdAt           = createdAt,
            updatedAt           = updatedAt
        )
    }
}




/*
// FILE: com/dogancaglar/paymentservice/domain/model/payment/InternalTransfer.kt
package com.dogancaglar.paymentservice.domain.model.payment

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.vo.InternalTransferId
import com.dogancaglar.paymentservice.domain.model.vo.TxId
import java.time.LocalDateTime

class InternalTransfer private constructor(
    val transferId: InternalTransferId,
    val sourceTransactionId: TxId,
    val amount: Amount,
    val reversedAmount: Amount,
    val targetAccount: String,
    val sourceAccount: String,
    val transferType: String, // ◄ 🎯 Added to aggregate instance configuration
    val status: InternalTransferStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {

    init {
        require(targetAccount.isNotBlank()) { "targetEntityId must not be blank" }
        require(sourceAccount.isNotBlank()) { "sourceEntityId must not be blank" }
        require(transferType.isNotBlank()) { "transferType must not be blank" } // ◄ Invariant validation
        require(amount.isPositive()) { "amount must be positive" }
        require(reversedAmount >= Amount.zero(amount.currency)) { "reversedAmount cannot be negative" }
        require(reversedAmount <= amount) { "reversedAmount cannot exceed total amount" }
    }

    fun markSentForTransfer(now: LocalDateTime = Utc.nowLocalDateTime()): InternalTransfer {
        require(status == InternalTransferStatus.CREATED_PENDING) { "Invalid state shift from $status" }
        return copy(status = InternalTransferStatus.SENT_FOR_TRANSFER, updatedAt = now)
    }

    fun markTransferred(now: LocalDateTime = Utc.nowLocalDateTime()): InternalTransfer {
        require(status == InternalTransferStatus.SENT_FOR_TRANSFER) { "Invalid state shift from $status" }
        return copy(status = InternalTransferStatus.TRANSFERRED, updatedAt = now)
    }

    private fun copy(
        status: InternalTransferStatus = this.status,
        updatedAt: LocalDateTime = Utc.nowLocalDateTime()
    ): InternalTransfer = InternalTransfer(
        transferId          = transferId,
        sourceTransactionId = sourceTransactionId,
        amount              = amount,
        reversedAmount      = reversedAmount,
        targetAccount       = targetAccount,
        sourceAccount       = sourceAccount,
        transferType        = transferType, // ◄ Propagated safely
        status              = status,
        createdAt           = createdAt,
        updatedAt           = updatedAt
    )

    companion object {
        fun createNew(
            transferId: InternalTransferId,
            sourceTransactionId: TxId,
            amount: Amount,
            sourceAccount: String,
            targetAccount: String,
            transferType: String, // ◄ 🎯 Added to primary initialization track
            now: LocalDateTime = Utc.nowLocalDateTime()
        ): InternalTransfer {
            return InternalTransfer(
                transferId          = transferId,
                sourceTransactionId = sourceTransactionId,
                amount              = amount,
                reversedAmount      = Amount.zero(amount.currency),
                targetAccount       = targetAccount,
                sourceAccount       = sourceAccount,
                transferType        = transferType,
                status              = InternalTransferStatus.CREATED_PENDING,
                createdAt           = now,
                updatedAt           = now
            )
        }

        fun rehydrate(
            transferId: InternalTransferId,
            sourceTransactionId: TxId,
            amount: Amount,
            reversedAmount: Amount,
            targetAccount: String,
            sourceAccount: String,
            transferType: String, // ◄ Added to persistence rehydration
            status: InternalTransferStatus,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): InternalTransfer = InternalTransfer(
            transferId          = transferId,
            sourceTransactionId = sourceTransactionId,
            amount              = amount,
            reversedAmount      = reversedAmount,
            targetAccount       = targetAccount,
            sourceAccount       = sourceAccount,
            transferType        = transferType,
            status              = status,
            createdAt           = createdAt,
            updatedAt           = updatedAt
        )
    }
}
 */