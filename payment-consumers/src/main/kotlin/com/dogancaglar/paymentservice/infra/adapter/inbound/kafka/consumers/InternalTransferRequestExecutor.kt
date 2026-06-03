package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.InternalTransferRequested
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.paymentservice.domain.model.ledger.TxStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.CentralDbTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class InternalTransferRequestExecutor(
    private val paymentRepository: PaymentRepository,
    private val paymentTxPort: PaymentTxPort,
    private val accountDirectory: AccountDirectoryPort,
    private val centralDbTransactionalFacadePort: CentralDbTransactionalFacadePort,
    private val dedupe: EventDeduplicationPort,
    private val idGeneratorPort: IdGeneratorPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.INTERNAL_TRANSFERS],
        containerFactory = "\${Topics.INTERNAL_TRANSFERS}-factory",
        groupId = CONSUMER_GROUPS.INTERNAL_TRANSFER_CONSUMER
    )
    fun onInternalTransferRequested(
        record: ConsumerRecord<String, EventEnvelope<InternalTransferRequested>>,
        consumer: Consumer<*, *>
    ) {
        val envelope = record.value()
        EventLogContext.with(envelope) {
            val eventId = envelope.eventId
            if (dedupe.exists(eventId)) {
                logger.warn("⚠️ Event is processed already, skipping eventId=\$eventId")
                return@with
            }

            val event = envelope.data
            logger.info("🎬 Processing InternalTransferRequested for target \${event.targetEntityId}")

            try {
                val paymentIntentIdValue = event.paymentIntentId.toLongOrNull() ?: 0L
                val paymentIntentId = PaymentIntentId(paymentIntentIdValue)
                val payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
                    ?: throw IllegalStateException("Payment not found for paymentIntentId=\${event.paymentIntentId}")

                // Resolve accounts
                val sourceAccountType = AccountType.valueOf(event.sourceAccountType)
                val merchantGrossPool = Account.fromProfile(accountDirectory.getAccountProfile(sourceAccountType, event.sourceAccountId))
                val targetAccountType = AccountType.valueOf(event.targetAccountType)
                val targetAccount = Account.fromProfile(accountDirectory.getAccountProfile(targetAccountType, event.targetEntityId))

                val amount = Amount.of(event.amountValue, Currency(event.currency))

                // 1. Find the capture TxId to act as parent for the internal transfer
                val txs = paymentTxPort.findByPaymentId(payment.paymentId.value)
                val captureTx = txs.find { it.txType == "CAPTURE" && it.status == TxStatus.SUCCESS }
                    ?: throw IllegalStateException("Successful CaptureTx not found for paymentId=\${payment.paymentId.value}")

                // 2. Generate new TxId for InternalTransfer
                val internalTransferTxIdValue = idGeneratorPort.nextPaymentId()

                // 3. Generate Tx instance
                val internalTx = Tx.createInternalTransferTx(
                    txId = TxId(internalTransferTxIdValue),
                    paymentId = payment.paymentId,
                    paymentIntentId = paymentIntentId,
                    captureTxId = captureTx.txId,
                    amount = amount,
                    status = TxStatus.SUCCESS
                )

                // 4. Generate JournalEntry based on target type
                val journalIdentifier = "\${event.publicPaymentIntentId}-\${captureTx.txId.value}"
                val journalEntries = when (targetAccountType) {
                    AccountType.PLATFORM_COMMISSION_ESCROW -> JournalEntry.commissionFeeRegistered(
                        paymentId = payment.paymentId,
                        txId = internalTx.txId,
                        journalIdentifier = journalIdentifier,
                        commissionFee = amount,
                        commissionFeeAccount = targetAccount,
                        merchantAccount = merchantGrossPool
                    )
                    AccountType.MARKETPLACE_SUB_SELLER -> JournalEntry.internalTransfer(
                        paymentId = payment.paymentId,
                        txId = internalTx.txId,
                        journalIdentifier = journalIdentifier,
                        amount = amount,
                        merchantGrossPool = merchantGrossPool,
                        targetAccount = targetAccount,
                        targetAccountType = targetAccountType,
                        targetEntityId = event.targetEntityId
                    )
                    else -> throw IllegalArgumentException("Unsupported target account type for split transfer: \$targetAccountType")
                }

                // 5. Save atomically (no updated payment state, new Tx, new Journals)
                centralDbTransactionalFacadePort.saveAtomically(
                    payment = null,
                    tx = internalTx,
                    journalEntries = journalEntries,
                    outboxEvents = emptyList()
                )
                logger.info("💾 Internal split transfer persisted successfully for sub-seller \${event.targetEntityId}")

                dedupe.markProcessed(eventId, 3600)
                consumer.commitSync()
            } catch (e: Exception) {
                logger.error("❌ Failed to process internal transfer for target \${event.targetEntityId}", e)
                throw e
            }
        }
    }
}
