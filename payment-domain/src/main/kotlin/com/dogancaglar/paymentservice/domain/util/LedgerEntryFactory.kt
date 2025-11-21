package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry
import java.time.Instant
import java.time.LocalDateTime

/**
 * Factory for creating LedgerEntry objects with proper validation and metadata.
 */
class LedgerEntryFactory {

    fun create(journalEntry: JournalEntry): LedgerEntry =
        LedgerEntry.create(
            ledgerEntryId = 0L, // Will be assigned by database
            journalEntry = journalEntry,
            createdAt = Utc.nowLocalDateTime()
        )

    fun fromPersistence(
        ledgerEntryId: Long,
        journalEntry: JournalEntry,
        createdAt: Instant
    ): LedgerEntry =
        LedgerEntry.create(
            ledgerEntryId = ledgerEntryId,
            journalEntry = journalEntry,
            createdAt = Utc.fromInstant(createdAt)
        )
}