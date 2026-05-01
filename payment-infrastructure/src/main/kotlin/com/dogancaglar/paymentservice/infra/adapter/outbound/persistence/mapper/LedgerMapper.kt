package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.JournalEntryEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.LedgerEntryEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PostingEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface LedgerMapper {
    fun insertJournalEntry(entry: JournalEntryEntity): Int
    fun insertLedgerEntry(entry: LedgerEntryEntity): Int
    fun insertPosting(posting: PostingEntity): Int

    // Query methods for testing
    fun findByJournalId(journalId: String): JournalEntryEntity?
    fun findPostingsByJournalId(journalId: String): List<PostingEntity>
}