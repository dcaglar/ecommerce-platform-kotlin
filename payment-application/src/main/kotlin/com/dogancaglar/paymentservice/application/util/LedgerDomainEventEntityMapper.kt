package com.dogancaglar.paymentservice.application.util

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.LedgerEntryEventData
import com.dogancaglar.paymentservice.application.events.PostingDirection
import com.dogancaglar.paymentservice.application.events.PostingEventData
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Posting
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId

object LedgerDomainEventEntityMapper {

    /**
     * Maps JournalEntry domain model to LedgerEntryEventData DTO.
     */
    fun toLedgerEntryEventData(journal: JournalEntry): LedgerEntryEventData {
        return LedgerEntryEventData.create(
            journalEntryId = journal.id,
            journalType = journal.txType,
            journalName = journal.name,
            paymentId = journal.paymentId.value,
            txId = journal.txId.value,
            createdAt = Utc.nowInstant(), // or a passed-in timestamp
            postings = journal.postings.map { toPostingEventData(it) }
        )
    }


    fun toDomain(ledgerEntryEvent: LedgerEntryEventData): JournalEntry {
        val postingsDomain = ledgerEntryEvent.postings.map { it.toDomain() }

        return JournalEntry.rehytrate(
            id = ledgerEntryEvent.journalEntryId,
            txType = ledgerEntryEvent.journalType,
            name = ledgerEntryEvent.journalName ?: "Ledger Entry",
            paymentId = PaymentId(ledgerEntryEvent.paymentId),
            txId = TxId(ledgerEntryEvent.txId),
            postings = postingsDomain
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
        val account = Account.Companion.create(accountType, entityId)
        val amountVo = Amount.Companion.of(amount, Currency(currency))
        return when (direction) {
            PostingDirection.DEBIT -> Posting.Debit.create(account, amountVo)
            PostingDirection.CREDIT -> Posting.Credit.create(account, amountVo)
        }
    }
}