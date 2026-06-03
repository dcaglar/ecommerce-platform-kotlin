package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import java.time.Instant

/**
 * Tx
 *
 * Sealed hierarchy representing every financial transaction record
 * persisted in the Central DB. Each subclass maps to a discrete
 * gateway or internal operation event.
 *
 * Design constraints (Golden Rules):
 *  - These are PURE DATA RECORDS. No domain logic, no side effects.
 *  - All IDs use the canonical typed value wrappers (PaymentId, TxId),
 *    never raw Long primitives, to prevent primitive obsession bugs
 *    (e.g., passing paymentId where txId is expected).
 *  - [status] uses the [TxStatus] enum so that all code paths handle
 *    PENDING, SUCCESS, and FAILED explicitly; no boolean flags.
 *  - [acquirerReference] is the external PSP/acquirer transaction reference.
 *    It is non-nullable on all terminal transactions; nullable only on
 *    AuthorizationTx where the reference may not yet be assigned.
 *
 * Subclasses:
 *  - [AuthorizationTx]: Records the AUTH_HOLD on the shopper's instrument.
 *  - [CaptureTx]:       Records a capture attempt against the authorization.
 *  - [RefundTx]:        Records a refund against a prior capture.
 *  - [SettleTx]:        Records the final acquirer settlement matching a capture.
 */
sealed class Tx {

    abstract val txId: TxId
    abstract val txType: String
    abstract val paymentId: PaymentId
    abstract val paymentIntentId: PaymentIntentId
    abstract val status: TxStatus
    abstract val amount: Amount
    abstract val createdAt: Instant

    // -------------------------------------------------------------------------
    // AuthorizationTx
    // -------------------------------------------------------------------------

    data class AuthorizationTx(
        override val txId: TxId,
        override val paymentId: PaymentId,
        override val paymentIntentId: PaymentIntentId,
        val acquirerReference: String,
        override val amount: Amount,
        override val status: TxStatus = TxStatus.SUCCESS,
        override val createdAt: Instant = Instant.now()
    ) : Tx() {
        override val txType = "AUTHORIZATION"
    }

    // -------------------------------------------------------------------------
    // CaptureTx
    // -------------------------------------------------------------------------

    data class CaptureTx(
        override val txId: TxId,
        override val paymentId: PaymentId,
        override val paymentIntentId: PaymentIntentId,
        val authorizationTxId: TxId,
        val acquirerReference: String,
        override val amount: Amount,
        override val status: TxStatus = TxStatus.PENDING,
        val settleStatus: SettleStatus = SettleStatus.UNMATCHED,
        override val createdAt: Instant = Instant.now()
    ) : Tx() {
        override val txType = "CAPTURE"
    }

    // -------------------------------------------------------------------------
    // RefundTx
    // -------------------------------------------------------------------------

    data class RefundTx(
        override val txId: TxId,
        override val paymentId: PaymentId,
        override val paymentIntentId: PaymentIntentId,
        val captureTxId: TxId,
        val acquirerReference: String,
        override val amount: Amount,
        override val status: TxStatus = TxStatus.PENDING,
        override val createdAt: Instant = Instant.now()
    ) : Tx() {
        override val txType = "REFUND"
    }

    // -------------------------------------------------------------------------
    // SettleTx
    // -------------------------------------------------------------------------

    data class SettleTx(
        override val txId: TxId,
        override val paymentId: PaymentId,
        override val paymentIntentId: PaymentIntentId,
        val captureTxId: TxId,
        val acquirerBatchReference: String,
        val settledAmount: Amount,
        override val amount: Amount,          // original capture amount for cross-check
        override val status: TxStatus = TxStatus.SUCCESS,
        override val createdAt: Instant = Instant.now()
    ) : Tx() {
        override val txType = "SETTLE"

        /**
         * Whether the settled amount matches the originally captured amount exactly.
         * A mismatch signals a discrepancy that requires an ADJUSTMENT journal entry.
         */
        val hasDiscrepancy: Boolean
            get() = settledAmount != amount
    }

    // -------------------------------------------------------------------------
    // Factory companion
    // -------------------------------------------------------------------------

    companion object {

        fun createAuthTx(
            txId: TxId,
            paymentId: PaymentId,
            paymentIntentId: PaymentIntentId,
            acquirerReference: String,
            amount: Amount,
            status: TxStatus = TxStatus.SUCCESS
        ): AuthorizationTx = AuthorizationTx(
            txId               = txId,
            paymentId          = paymentId,
            paymentIntentId    = paymentIntentId,
            acquirerReference  = acquirerReference,
            amount             = amount,
            status             = status
        )

        fun createCaptureTx(
            txId: TxId,
            paymentId: PaymentId,
            paymentIntentId: PaymentIntentId,
            authorizationTxId: TxId,
            acquirerReference: String,
            amount: Amount,
            status: TxStatus = TxStatus.PENDING
        ): CaptureTx = CaptureTx(
            txId              = txId,
            paymentId         = paymentId,
            paymentIntentId   = paymentIntentId,
            authorizationTxId = authorizationTxId,
            acquirerReference = acquirerReference,
            amount            = amount,
            status            = status
        )

        fun createRefundTx(
            txId: TxId,
            paymentId: PaymentId,
            paymentIntentId: PaymentIntentId,
            captureTxId: TxId,
            acquirerReference: String,
            amount: Amount,
            status: TxStatus = TxStatus.PENDING
        ): RefundTx = RefundTx(
            txId              = txId,
            paymentId         = paymentId,
            paymentIntentId   = paymentIntentId,
            captureTxId       = captureTxId,
            acquirerReference = acquirerReference,
            amount            = amount,
            status            = status
        )

        fun createSettleTx(
            txId: TxId,
            paymentId: PaymentId,
            paymentIntentId: PaymentIntentId,
            captureTxId: TxId,
            acquirerBatchReference: String,
            settledAmount: Amount,
            originalCaptureAmount: Amount
        ): SettleTx = SettleTx(
            txId                    = txId,
            paymentId               = paymentId,
            paymentIntentId         = paymentIntentId,
            captureTxId             = captureTxId,
            acquirerBatchReference  = acquirerBatchReference,
            settledAmount           = settledAmount,
            amount                  = originalCaptureAmount

        )


        fun internalTransferTx(txId: TxId,paymentId: PaymentId,paymentIntentId: PaymentIntentId,captureTxId: TxId,acquirerReference: String
        ,transferAmount: Amount,status: TxStatus= TxStatus.PENDING
        )
    }


    /*
       val internalTx = Tx.createInternalTransferTx(
                    txId = TxId(internalTransferTxIdValue),
                    paymentId = payment.paymentId,
                    paymentIntentId = paymentIntentId,
                    captureTxId = TxId(captureTxIdValue), // Using captureTx as parent
                    amount = txAmount,
                    status = TxStatus.SUCCESS
                )
     */
}
