package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.event.LedgerEntryEventData
import com.dogancaglar.paymentservice.domain.event.PostingDirection
import com.dogancaglar.paymentservice.domain.event.PostingEventData
import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Posting

/**
 * Maps between domain LedgerEntry aggregates and their event representations.
 * Mirrors the structure of PaymentOrderDomainEventMapper for consistency.
 * Used for event serialization when publishing LedgerEntriesRecorded.
 */
object LedgerDomainEventMapper {

    /**
     * Maps LedgerEntry domain model to LedgerEntryEventData DTO.
     */
    fun toLedgerEntryEventData(ledgerEntry: LedgerEntry): LedgerEntryEventData {
        val journal = ledgerEntry.journalEntry
        return LedgerEntryEventData.create(
            ledgerEntryId = ledgerEntry.ledgerEntryId,
            journalEntryId = journal.id,
            journalType = journal.txType,
            journalName = journal.name,
            createdAt = ledgerEntry.createdAt,
            postings = journal.postings.map { toPostingEventData(it) }
        )
    }

    /**
     * Maps domain Posting to PostingEventData DTO.
     * Encapsulates the mapping logic for converting sealed Posting classes to event DTOs.
     */
    fun toPostingEventData(posting: Posting): PostingEventData {
        val direction = when (posting) {
            is Posting.Debit -> PostingDirection.DEBIT
            is Posting.Credit -> PostingDirection.CREDIT
        }
        return PostingEventData.create(
            accountCode = posting.account.accountCode,
            accountType = posting.account.type,
            amount = posting.amount.quantity,
            currency = posting.amount.currency.currencyCode,
            direction = direction
        )
    }
}