package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.application.model.LedgerEntry
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import java.time.Clock
import java.time.LocalDateTime

/**
 * Factory for creating LedgerEntry objects with proper validation and metadata.
 */
class LedgerEntryFactory(
    private val clock: Clock
) {

    fun create(journalEntry: JournalEntry): LedgerEntry =
        LedgerEntry.create(
            ledgerEntryId = 0L, // Will be assigned by database
            journalEntry = journalEntry,
            createdAt = LocalDateTime.now(clock)
        )

    fun fromPersistence(
        ledgerEntryId: Long,
        journalEntry: JournalEntry,
        createdAt: LocalDateTime
    ): LedgerEntry =
        LedgerEntry.create(
            ledgerEntryId = ledgerEntryId,
            journalEntry = journalEntry,
            createdAt = createdAt
        )
}

