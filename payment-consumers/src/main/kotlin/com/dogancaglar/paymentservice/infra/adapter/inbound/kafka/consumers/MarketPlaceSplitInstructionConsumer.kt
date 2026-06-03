package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.paymentservice.domain.model.ledger.TxStatus
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.CentralDbTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.apache.kafka.clients.consumer.Consumer

@Component
class MarketPlaceSplitInstructionConsumer(
    private val paymentRepository: PaymentRepository,
    private val paymentTxPort: PaymentTxPort,
    private val accountDirectory: AccountDirectoryPort,
    private val centralDbTransactionalFacadePort: CentralDbTransactionalFacadePort,
    private val dedupe: EventDeduplicationPort,
    private val objectMapper: ObjectMapper,
    private val idGeneratorPort: IdGeneratorPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.LEDGER_ENTRIES_RECORDED],
        containerFactory = "ledger-entries-recorded-factory", // or whatever the factory is named, maybe default
        groupId = CONSUMER_GROUPS.MARKETPLACE_SPLIT_INSTRUCTION_CONSUMER
    )
    fun onLedgerEntriesRecorded(
        record: ConsumerRecord<String, EventEnvelope<com.dogancaglar.common.event.Event>>,
        consumer: Consumer<*, *>
    ) {
        val envelope = record.value() as EventEnvelope<LedgerEntriesRecorded>
        val eventId = envelope.eventId
        if (dedupe.exists(eventId)) {
            logger.warn("⚠️ Event is processed already, skipping eventId=\$eventId")
            return
        }

        val event = envelope.data
        logger.info("🎬 Processing LedgerEntriesRecorded event for paymentIntentId: \${event.publicPaymentIntentId}")

        try {
            // Check if this batch contains a CAPTURE journal entry
            val captureEntry = event.ledgerEntries.find { it.journalType == JournalType.CAPTURE }
            if (captureEntry == null) {
                logger.debug("No CAPTURE journal entry in this batch. Ignoring.")
                dedupe.markProcessed(eventId, 3600)
                consumer.commitSync()
                return
            }
            val txs = paymentTxPort.findByPaymentId(event.paymentOrderId)
            val paymentIntentIdValue = event.paymentIntentId.toLongOrNull() ?: 0L
            val paymentIntentId = PaymentIntentId(paymentIntentIdValue)
            val payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
                ?: throw IllegalStateException("Payment not found for paymentIntentId=\${event.paymentIntentId}")

            if (payment.splits.isEmpty()) {
                logger.debug("Payment has no splits. Ignoring.")
                dedupe.markProcessed(eventId, 3600)
                consumer.commitSync()
                return
            }

            // At this point we know we have a CAPTURE and we have splits.
            logger.info("MARKETPLACE transaction, executing internal transfers for \${payment.splits.size} splits")

            val captureTxIdValue = captureEntry.txId
            val merchantGrossPool = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.MARKETPLACE_OPERATOR, event.sellerId ?: "UNKNOWN"))
            val journalIdentifier = "\${event.publicPaymentIntentId}-\${captureTxIdValue}"

            payment.splits.forEachIndexed { index, split ->
                val targetAccount = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.MARKETPLACE_SUB_SELLER, split.targetEntityId))
                
                // 1. Generate new TxId for InternalTransfer
                val internalTransferTxIdValue = idGeneratorPort.nextPaymentId()
                
                // 2. Generate Tx instance
                val txAmount = Amount.of(split.amount.quantity, Currency(split.amount.currency.currencyCode))
                val internalTx = Tx.createInternalTransferTx(
                    txId = TxId(internalTransferTxIdValue),
                    paymentId = payment.paymentId,
                    paymentIntentId = paymentIntentId,
                    captureTxId = TxId(captureTxIdValue), // Using captureTx as parent
                    amount = txAmount,
                    status = TxStatus.SUCCESS
                )

                // 3. Generate JournalEntry
                val journalEntries = JournalEntry.executeSubSellerSplit(
                    paymentId = payment.paymentId,
                    txId = internalTx.txId,
                    journalIdentifier = "$journalIdentifier-$index",
                    merchantGrossPool = merchantGrossPool,
                    splits = listOf(split),
                    resolveTargetAccount = { _, _ -> targetAccount }
                )

                // 4. Save atomically (no updated payment state, new Tx, new Journals)
                centralDbTransactionalFacadePort.saveAtomically(
                    payment = null,
                    tx = internalTx,
                    journalEntries = journalEntries,
                    outboxEvents = emptyList()
                )
                logger.info("💾 Internal split transfer persisted successfully for sub-seller \${split.targetEntityId}")
            }

            dedupe.markProcessed(eventId, 3600)
            consumer.commitSync()
        } catch (e: Exception) {
            logger.error("❌ Failed to process MarketPlace splits for paymentIntentId: \${event.publicPaymentIntentId}", e)
            throw e
        }
    }
}
