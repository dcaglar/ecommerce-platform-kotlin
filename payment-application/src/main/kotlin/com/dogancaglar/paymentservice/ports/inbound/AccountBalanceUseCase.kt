package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry

/**
 * Use case for updating account balances from ledger entries.
 */
interface AccountBalanceUseCase {
    /**
     * Updates account balances from a batch of ledger entries.
     * Performs idempotency checks and updates Redis deltas.
     * 
     * @param ledgerEntries List of ledger entries to process
     * @return List of processed ledger entry IDs (already persisted, safe to mark as processed)
     */
    fun updateAccountBalancesBatch(
        ledgerEntries: List<LedgerEntry>
    ): List<Long>
}

