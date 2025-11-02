package com.dogancaglar.paymentservice.domain.model.ledger

import java.time.LocalDateTime

/**
 * Application layer wrapper for JournalEntry with persistence metadata.
 * 
 * ⚠️ Use LedgerEntryFactory to create instances. The companion.create() method is only
 * for factory usage within this module and should not be called directly by application code.
 */
class  LedgerEntry private constructor (
    var ledgerEntryId: Long, // Mutable: populated by persistence layer after insert
    val journalEntry: JournalEntry,
    val createdAt: LocalDateTime
) {
    companion object {
        /**
         * Creates a LedgerEntry.
         * ⚠️ This is intended for use by LedgerEntryFactory only.
         * For application code, use LedgerEntryFactory instead.
         */
        fun create(
            ledgerEntryId: Long,
            journalEntry: JournalEntry,
            createdAt: LocalDateTime
        ): LedgerEntry = LedgerEntry(ledgerEntryId, journalEntry, createdAt)
    }
}