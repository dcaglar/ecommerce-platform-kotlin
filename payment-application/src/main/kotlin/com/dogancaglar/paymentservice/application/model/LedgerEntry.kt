package com.dogancaglar.paymentservice.application.model

import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import java.time.LocalDateTime

data class LedgerEntry(
    val ledgerEntryId: Long,
    val journalEntry: JournalEntry,
    val createdAt: LocalDateTime
)