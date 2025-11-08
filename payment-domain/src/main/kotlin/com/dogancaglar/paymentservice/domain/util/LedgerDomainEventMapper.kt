package com.dogancaglar.paymentservice.domain.util

import com.dogancaglar.paymentservice.domain.event.LedgerEntryEventData
import com.dogancaglar.paymentservice.domain.event.PostingDirection
import com.dogancaglar.paymentservice.domain.event.PostingEventData
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Posting

/**
 * Maps between domain LedgerEntry aggregates and their event representations.
 * Mirrors the structure of PaymentOrderDomainEventMapper for consistency.
 * Used for event serialization when publishing LedgerEntriesRecorded.
 */
import java.time.Clock

object LedgerDomainEventMapper {

    private val ledgerEntryFactory = LedgerEntryFactory(Clock.systemUTC())

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


    fun toDomain(ledgerEntryEvent: LedgerEntryEventData): LedgerEntry {
        val postingsDomain = ledgerEntryEvent.postings.map { it.toDomain() }

        val journal = JournalEntry.fromPersistence(
            id = ledgerEntryEvent.journalEntryId,
            txType = ledgerEntryEvent.journalType,
            name = ledgerEntryEvent.journalName ?: "Ledger Entry",
            postings = postingsDomain
        )

        return ledgerEntryFactory.fromPersistence(
            ledgerEntryId = ledgerEntryEvent.ledgerEntryId,
            journalEntry = journal,
            createdAt = ledgerEntryEvent.createdAt
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

    fun PostingEventData.toDomain(): Posting {
        val entityId = run {
            val afterType = accountCode.substringAfter('.', "GLOBAL")
            val beforeCurrency = afterType.substringBeforeLast('.', afterType)
            if (beforeCurrency.isBlank()) "GLOBAL" else beforeCurrency
        }
        val account = Account.create(accountType, entityId)
        val amountVo = Amount.of(amount, Currency(currency))
        return when (direction) {
            PostingDirection.DEBIT -> Posting.Debit.create(account, amountVo)
            PostingDirection.CREDIT -> Posting.Credit.create(account, amountVo)
        }
    }
}