package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry

interface LedgerEntryPort {
    /**
     * Persists ledger entries atomically and populates the ledgerEntryId in each entry.
     * @return List of ledger entries with populated IDs (same instances, IDs updated in-place)
     */
    fun postLedgerEntriesAtomic(entries: List<LedgerEntry>): List<LedgerEntry>
}