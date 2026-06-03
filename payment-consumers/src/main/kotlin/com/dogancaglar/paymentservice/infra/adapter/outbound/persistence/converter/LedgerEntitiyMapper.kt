package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.JournalEntryEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PostingEntity
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Posting
import java.time.ZoneOffset

internal object LedgerEntitiyMapper {

    fun toJournalEntryEntity(entry: JournalEntry): JournalEntryEntity =
        JournalEntryEntity(
            id = entry.id,
            txType = entry.txType.name,
            name = entry.name,
            referenceType = entry.paymentId.value.toString(),
            referenceId = entry.txId.value.toString(),
            createdAt = java.time.Instant.now(),
        )

    fun toPostingEntities(entry: JournalEntry): List<PostingEntity> =
        entry.postings.map { posting ->
            PostingEntity(
                journalId = entry.id,
                accountCode = posting.account.accountCode,
                accountType = posting.account.type.name,
                amount = posting.amount.quantity,
                direction = when (posting) {
                    is Posting.Debit -> "DEBIT"
                    is Posting.Credit -> "CREDIT"
                },
                currency = posting.amount.currency.currencyCode,
                createdAt = java.time.Instant.now(),
            )
        }
}
