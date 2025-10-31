package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.JournalEntryEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PostingEntity
import com.dogancaglar.paymentservice.application.model.LedgerEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Posting

  internal object LedgerPersistenceMapper {

    fun toJournalEntryEntity(entry: LedgerEntry): JournalEntryEntity =
        JournalEntryEntity(
            id = entry.journalEntry.id,
            txType = entry.journalEntry.txType.name,
            name = entry.journalEntry.name,
            referenceType = entry.journalEntry.referenceType,
            referenceId = entry.journalEntry.referenceId,
            createdAt = entry.createdAt
        )

    fun toPostingEntities(entry: LedgerEntry): List<PostingEntity> =
        entry.journalEntry.postings.map { posting ->
            PostingEntity(
                journalId = entry.journalEntry.id,
                accountCode = posting.account.accountCode,
                accountType = posting.account.type.name,
                amount = posting.amount.quantity,
                direction = when (posting) {
                    is Posting.Debit -> "DEBIT"
                    is Posting.Credit -> "CREDIT"
                },
                currency = posting.amount.currency.currencyCode,
                createdAt = entry.createdAt
            )
        }
}