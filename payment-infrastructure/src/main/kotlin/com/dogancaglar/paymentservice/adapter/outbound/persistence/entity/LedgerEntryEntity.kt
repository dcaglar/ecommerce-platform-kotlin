package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.LocalDateTime

/**
 * Entity representing a ledger entry in the database.
 * Each ledger entry has an auto-generated ID and references a journal entry.
 */
data class LedgerEntryEntity internal constructor(
    var id: Long? = null, // Auto-generated BIGSERIAL, populated by MyBatis after insert
    val journalId: String, // Foreign key to journal_entries.id
    val createdAt: LocalDateTime
)