package com.dogancaglar.paymentservice.domain.model.ledger

/**
 * TxStatus
 *
 * Represents the lifecycle state of any Tx record.
 *
 *  - PENDING:  The transaction has been created and is awaiting an external
 *              confirmation (e.g., a capture ACK from the gateway, a settle
 *              confirmation from the acquirer). No money movement is final.
 *
 *  - SUCCESS:  The gateway or acquirer has confirmed the operation. Money
 *              movement is irreversible. Ledger postings may now be applied.
 *
 *  - FAILED:   The operation was rejected or timed out past the retry window.
 *              The transaction is terminal. A corrective action (void/refund)
 *              may need to be initiated separately.
 */
enum class TxStatus {
    PENDING,
    SUCCESS,
    FAILED
}

/**
 * SettleStatus
 *
 * Tracks the reconciliation state of a CaptureTx against an incoming
 * acquirer settlement batch.
 *
 *  - UNMATCHED:     The capture has not yet been seen in any settlement batch.
 *                   Normal state shortly after capture; expected to resolve
 *                   within the acquirer's T+N settlement cycle.
 *
 *  - MATCHED:       The capture amount in the settlement batch exactly matches
 *                   the authorized capture amount. No further action required.
 *
 *  - DISCREPANCY:   The settlement batch amount differs from the expected
 *                   capture amount. Requires manual review and an ADJUSTMENT
 *                   journal entry to reconcile the ledger.
 */
enum class SettleStatus {
    UNMATCHED,
    MATCHED,
    DISCREPANCY
}
