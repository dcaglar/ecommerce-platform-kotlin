package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.JournalEntriesRecorded
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import com.dogancaglar.paymentservice.domain.model.ledger.TxStatus
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.payment.InternalTransferStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.inbound.usecases.RecordInternalTransferSubmissionUseCase
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

@Component
class MarketPlaceSplitInstructionConsumer(
    private val paymentRepository: PaymentRepository,
    private val paymentTxPort: PaymentTxPort,
    private val accountDirectory: AccountDirectoryPort,
    private val centralDbTransactionalFacadePort: CentralDbTransactionalFacadePort,
    private val dedupe: EventDeduplicationPort,
    private val objectMapsper: ObjectMapper,
    private val idGeneratorPort: IdGeneratorPort,
    private val recordInternalTransferSubmissionUseCase: RecordInternalTransferSubmissionUseCase
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.JOURNAL_ENTRIES_RECORDED],
        containerFactory = CONSUMER_GROUPS.MARKETPLACE_SPLIT_INSTRUCTION_CONSUMER + "-factory",
        groupId = CONSUMER_GROUPS.MARKETPLACE_SPLIT_INSTRUCTION_CONSUMER
    )
    fun onLedgerEntriesRecorded(
        record: ConsumerRecord<String, EventEnvelope<JournalEntriesRecorded>>
    ) {
        val envelope = record.value() as EventEnvelope<JournalEntriesRecorded>
        EventLogContext.with(envelope) {
            val eventId = envelope.eventId
            if (dedupe.exists(eventId)) {
                logger.warn("⚠️ Event is processed already, skipping eventId=\$eventId")
                return@with
            }

            val event = envelope.data
            logger.info("🎬 Processing JournalEntriesRecorded event for paymentIntentId: \${event.publicPaymentIntentId}")

            try {
                // Check if this batch contains a CAPTURE journal entry
                val captureEntry = event.ledgerEntries.find { it.journalType == JournalType.CAPTURE }
                if (captureEntry == null) {
                    logger.debug("No CAPTURE journal entry in this batch. Ignoring.")
                    dedupe.markProcessed(eventId, 3600)
                    return@with
                }
                val paymentIntentIdValue = event.paymentIntentId.toLongOrNull() ?: 0L
                val paymentIntentId = PaymentIntentId(paymentIntentIdValue)
                val payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
                    ?: throw IllegalStateException("Payment not found for paymentIntentId=\${event.paymentIntentId}")

                if (payment.splits.isEmpty()) {
                    logger.debug("Payment has no splits. Ignoring.")
                    dedupe.markProcessed(eventId, 3600)
                    return@with
                }

                // At this point we know we have a CAPTURE and we have splits.
                logger.info("MARKETPLACE transaction, executing internal transfers for \${payment.splits.size} splits")

                val txs = paymentTxPort.findByPaymentId(payment.paymentId.value)
                val captureTx = txs.find { it.txType == "CAPTURE" && it.status == TxStatus.SUCCESS }
                    ?: throw IllegalStateException("Successful CaptureTx not found for paymentId=\${payment.paymentId.value}")

                val now = com.dogancaglar.common.time.Utc.nowInstant()

                payment.splits.forEachIndexed { _, split ->
                    recordInternalTransferSubmissionUseCase.recordSubmission(
                        paymentId = payment.paymentId,
                        paymentIntentId = paymentIntentId,
                        publicPaymentIntentId = event.publicPaymentIntentId,
                        captureTxId = captureTx.txId,
                        sourceAccountType = AccountType.MARKETPLACE_OPERATOR,
                        sourceEntityId = payment.merchantAccountId,
                        split = split
                    )
                }

                logger.info("💾 Internal split transfers staged and outbox events persisted successfully for \${payment.splits.size} sub-sellers")

                dedupe.markProcessed(eventId, 3600)
            } catch (e: Exception) {
                logger.error("❌ Failed to process MarketPlace splits for paymentIntentId: \${event.publicPaymentIntentId}", e)
                throw e
            }
        }
    }
}

