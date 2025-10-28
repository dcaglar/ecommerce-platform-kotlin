package com.dogancaglar.paymentservice.service

import com.dogancaglar.paymentservice.application.model.LedgerEntry
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.LedgerMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.LedgerPersistenceMapper
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
open class LedgerEntryTxAdapter(
    private val ledgerMapper: LedgerMapper
) : LedgerEntryPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(timeout = 3)
    override fun postLedgerEntriesAtomic(entries: List<LedgerEntry>) {
        if (entries.isEmpty()) return

        entries.forEach { entry ->
            val journalEntity = LedgerPersistenceMapper.toJournalEntryEntity(entry)
            val inserted = ledgerMapper.insertJournalEntry(journalEntity)
            if (inserted == 0) {
                logger.debug("🟦 Duplicate journal entry id={} — skipping insert", journalEntity.id)
                return
            }

            LedgerPersistenceMapper.toPostingEntities(entry).forEach { posting ->
                ledgerMapper.insertPosting(posting)
            }
        }

        logger.info("💾 Ledger batch persisted with {} entries", entries.size)
    }

}