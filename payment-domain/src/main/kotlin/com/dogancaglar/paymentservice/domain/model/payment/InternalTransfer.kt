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
 * @param targetEntityId      The specific entity ID of the destination account.
 * @param sourceAccountType   The type of the source account.
 * @param sourceEntityId      The specific entity ID of the source account.
 * @param status              Current lifecycle state of this transfer.
 * @param createdAt           Timestamp of aggregate creation (UTC).
 * @param updatedAt           Timestamp of last state mutation (UTC).
 */
class InternalTransfer private constructor(
    val transferId: InternalTransferId,
    val sourceTransactionId: TxId,
    val amount: Amount,
    val reversedAmount: Amount,
    val targetAccountType: AccountType,
    val targetEntityId: String,
    val sourceAccountType: AccountType,
    val sourceEntityId: String,
    val status: InternalTransferStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {

    // =========================================================================
    // Aggregate Invariants (enforced on every construction path)
    // =========================================================================

    init {
        require(targetEntityId.isNotBlank()) {
            "targetEntityId must not be blank"
        }
        require(sourceEntityId.isNotBlank()) {
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
        targetAccountType   = targetAccountType,
        targetEntityId      = targetEntityId,
        sourceAccountType   = sourceAccountType,
        sourceEntityId      = sourceEntityId,
        status              = status,
        createdAt           = createdAt,
        updatedAt           = updatedAt
    )

    // =========================================================================
    // Display
    // =========================================================================

    override fun toString(): String =
        "InternalTransfer(transferId=${transferId.value}, sourceTransactionId=${sourceTransactionId.value}, " +
        "amount=$amount, reversedAmount=$reversedAmount, target=$targetAccountType/$targetEntityId, " +
        "source=$sourceAccountType/$sourceEntityId, status=$status, createdAt=$createdAt, updatedAt=$updatedAt)"

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
            targetAccountType: AccountType,
            targetEntityId: String,
            sourceAccountType: AccountType,
            sourceEntityId: String,
            now: LocalDateTime = Utc.nowLocalDateTime()
        ): InternalTransfer {
            return InternalTransfer(
                transferId          = transferId,
                sourceTransactionId = sourceTransactionId,
                amount              = amount,
                reversedAmount      = Amount.zero(amount.currency),
                targetAccountType   = targetAccountType,
                targetEntityId      = targetEntityId,
                sourceAccountType   = sourceAccountType,
                sourceEntityId      = sourceEntityId,
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
            targetAccountType: AccountType,
            targetEntityId: String,
            sourceAccountType: AccountType,
            sourceEntityId: String,
            status: InternalTransferStatus,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): InternalTransfer = InternalTransfer(
            transferId          = transferId,
            sourceTransactionId = sourceTransactionId,
            amount              = amount,
            reversedAmount      = reversedAmount,
            targetAccountType   = targetAccountType,
            targetEntityId      = targetEntityId,
            sourceAccountType   = sourceAccountType,
            sourceEntityId      = sourceEntityId,
            status              = status,
            createdAt           = createdAt,
            updatedAt           = updatedAt
        )
    }
}