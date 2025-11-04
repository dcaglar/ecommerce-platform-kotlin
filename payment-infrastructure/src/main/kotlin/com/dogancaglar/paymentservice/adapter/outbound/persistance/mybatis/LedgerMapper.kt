package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.JournalEntryEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PostingEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface LedgerMapper {
    fun insertJournalEntry(entry: JournalEntryEntity): Int
    fun insertLedgerEntry(entry: com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.LedgerEntryEntity): Int
    fun insertPosting(posting: PostingEntity): Int
    
    // Query methods for testing
    fun findByJournalId(journalId: String): JournalEntryEntity?
    fun findPostingsByJournalId(journalId: String): List<PostingEntity>
}