package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.LedgerMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.LedgerPersistenceMapper
import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
open class LedgerEntryTxAdapter(
    private val ledgerMapper: LedgerMapper
) : LedgerEntryPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(timeout = 3)
    override fun postLedgerEntriesAtomic(entries: List<LedgerEntry>): List<LedgerEntry> {
        if (entries.isEmpty()) return emptyList()

        val persistedEntries = mutableListOf<LedgerEntry>()

        entries.forEach { entry ->
            // 1. Insert journal entry (idempotent via ON CONFLICT)
            val journalEntity = LedgerPersistenceMapper.toJournalEntryEntity(entry)
            val journalInserted = ledgerMapper.insertJournalEntry(journalEntity)
            if (journalInserted == 0) {
                logger.debug("🟦 Duplicate journal entry id={} — skipping insert", journalEntity.id)
                return emptyList()
            }

            // 2. Insert ledger entry (auto-generates ID)
            val ledgerEntity = LedgerPersistenceMapper.toLedgerEntryEntity(entry)
            ledgerMapper.insertLedgerEntry(ledgerEntity)
            val ledgerEntryId = ledgerEntity.id ?: throw IllegalStateException("Ledger entry ID was not generated")
            
            // 3. Populate ID in the LedgerEntry object
            entry.ledgerEntryId = ledgerEntryId
            
            // 4. Insert postings
            LedgerPersistenceMapper.toPostingEntities(entry).forEach { posting ->
                ledgerMapper.insertPosting(posting)
            }
            
            persistedEntries.add(entry)
            logger.debug("💾 Ledger entry persisted with id={} for journal={}", ledgerEntryId, journalEntity.id)
        }

        logger.info("💾 Ledger batch persisted with {} entries", persistedEntries.size)
        return persistedEntries
    }

}