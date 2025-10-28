package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.LedgerMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.LedgerPersistenceMapper
import com.dogancaglar.paymentservice.application.model.LedgerEntry
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class LedgerEntryAdapter(
    private val ledgerMapper: LedgerMapper
) : LedgerEntryPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun appendLedgerEntry(entry: LedgerEntry) {
        val journalEntity = LedgerPersistenceMapper.toJournalEntryEntity(entry)
        val inserted = ledgerMapper.insertJournalEntry(journalEntity)

        if (inserted == 0) {
            logger.info("🟦 Duplicate journal entry id={} — skipping insert", journalEntity.id)
            return
        }

        LedgerPersistenceMapper.toPostingEntities(entry).forEach { postingEntity ->
            ledgerMapper.insertPosting(postingEntity)
        }

        logger.info(
            "💾 Ledger entry persisted for journalId={} with {} postings",
            journalEntity.id,
            entry.journalEntry.postings.size
        )
    }
}