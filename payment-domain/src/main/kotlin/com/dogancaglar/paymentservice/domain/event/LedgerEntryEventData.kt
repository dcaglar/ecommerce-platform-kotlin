package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import java.time.LocalDateTime

/**
 * Serializable DTO representing a ledger entry for event publication.
 * Contains the essential information needed by consumers (e.g., AccountBalanceConsumer)
 * to process balance updates without querying the database.
 * 
 * This class is only created through the factory method to ensure invariants are maintained.
 */
data class LedgerEntryEventData private constructor(
    val ledgerEntryId: Long,
    val journalEntryId: String, // e.g., "AUTH:paymentorder-123"
    val journalType: JournalType,
    val journalName: String?,
    val createdAt: LocalDateTime,
    val postings: List<PostingEventData>
) {
    companion object {
        /**
         * Factory method to create LedgerEntryEventData.
         * 
         * @param ledgerEntryId Ledger entry ID
         * @param journalEntryId Journal entry ID (e.g., "AUTH:paymentorder-123")
         * @param journalType Journal type
         * @param journalName Journal name
         * @param createdAt Timestamp when ledger entry was created
         * @param postings List of postings for this ledger entry
         * @return LedgerEntryEventData instance
         */
        fun create(
            ledgerEntryId: Long,
            journalEntryId: String,
            journalType: JournalType,
            journalName: String?,
            createdAt: LocalDateTime,
            postings: List<PostingEventData>
        ): LedgerEntryEventData {
            return LedgerEntryEventData(
                ledgerEntryId = ledgerEntryId,
                journalEntryId = journalEntryId,
                journalType = journalType,
                journalName = journalName,
                createdAt = createdAt,
                postings = postings
            )
        }
    }
}


