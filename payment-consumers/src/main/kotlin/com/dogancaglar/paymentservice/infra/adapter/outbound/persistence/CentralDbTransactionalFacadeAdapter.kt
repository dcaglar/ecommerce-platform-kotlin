package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.LedgerMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter.LedgerEntitiyMapper

import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.paymentservice.domain.model.payment.Payment
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.PaymentMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.PaymentTxMapper
import com.dogancaglar.paymentservice.ports.outbound.CentralDbTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxWriterPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter.PaymentEntityMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter.PaymentTxEntityMapper

@Repository
open class CentralDbTransactionalFacadeAdapter(
    private val ledgerMapper: LedgerMapper,
    private val paymentMapper: PaymentMapper,
    private val txMapper: PaymentTxMapper,
    private val paymentEntityMapper: PaymentEntityMapper,
    private val localOutboxWriterPort: LocalOutboxWriterPort
) : CentralDbTransactionalFacadePort {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(timeout = 5)
    override fun saveAtomically(
        payment: Payment?,
        tx: Tx?,
        journalEntries: List<JournalEntry>,
        outboxEvents: List<OutboxEvent>
    ) {
        if (payment != null) {
            val paymentEntity = paymentEntityMapper.toEntity(payment)
            paymentMapper.upsert(paymentEntity)
        }

        if (tx != null) {
            val txEntity = PaymentTxEntityMapper.toEntity(tx)
            txMapper.upsert(txEntity)
        }

        if (journalEntries.isNotEmpty()) {
            journalEntries.forEach { entry ->
                // 1. Insert journal entry (idempotent via ON CONFLICT)
                val journalEntity = LedgerEntitiyMapper.toJournalEntryEntity(entry)
                val journalInserted = ledgerMapper.insertJournalEntry(journalEntity)
                if (journalInserted == 0) {
                    logger.debug("🟦 Duplicate journal entry id={} — skipping insert", journalEntity.id)
                    return@forEach
                }

                // 2. Insert postings
                LedgerEntitiyMapper.toPostingEntities(entry).forEach { posting ->
                    ledgerMapper.insertPosting(posting)
                }
                
                logger.debug("💾 Journal entry persisted with id={}", journalEntity.id)
            }
            logger.info("💾 Journal batch persisted with {} entries", journalEntries.size)
        }

        if (outboxEvents.isNotEmpty()) {
            localOutboxWriterPort.saveAll(outboxEvents)
            logger.info("💾 Outbox batch persisted with {} events", outboxEvents.size)
        }
    }
}