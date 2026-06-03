package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.JournalEntriesRecorded
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.paymentservice.domain.model.ledger.TxStatus
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.paymentservice.application.events.InternalTransferRequested
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
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
        topics = [Topics.JOURNAL_ENTRIES_RECORDED],
        containerFactory = "\${Topics.JOURNAL_ENTRIES_RECORDED}-factory",
        groupId = CONSUMER_GROUPS.MARKETPLACE_SPLIT_INSTRUCTION_CONSUMER
    )
    fun onLedgerEntriesRecorded(
        record: ConsumerRecord<String, EventEnvelope<com.dogancaglar.common.event.Event>>,
        consumer: Consumer<*, *>
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
                    consumer.commitSync()
                    return@with
                }
                val paymentIntentIdValue = event.paymentIntentId.toLongOrNull() ?: 0L
                val paymentIntentId = PaymentIntentId(paymentIntentIdValue)
                val payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
                    ?: throw IllegalStateException("Payment not found for paymentIntentId=\${event.paymentIntentId}")

                if (payment.splits.isEmpty()) {
                    logger.debug("Payment has no splits. Ignoring.")
                    dedupe.markProcessed(eventId, 3600)
                    consumer.commitSync()
                    return@with
                }

                // At this point we know we have a CAPTURE and we have splits.
                logger.info("MARKETPLACE transaction, executing internal transfers for \${payment.splits.size} splits")

                val outboxEvents = mutableListOf<OutboxEvent>()
                val now = com.dogancaglar.common.time.Utc.nowInstant()

                payment.splits.forEachIndexed { _, split ->
                    val transferRequested = InternalTransferRequested.from(
                        paymentIntentId = event.paymentIntentId,
                        publicPaymentIntentId = event.publicPaymentIntentId,
                        sourceAccountType = AccountType.MARKETPLACE_OPERATOR.name,
                        sourceAccountId = event.sellerId ?: "UNKNOWN",
                        targetAccountType = split.targetAccountType.name,
                        targetEntityId = split.targetEntityId,
                        amountValue = split.amount.quantity,
                        currency = split.amount.currency.currencyCode,
                        now = now
                    )

                    val transferEnvelope = EventEnvelopeFactory.envelopeFor(
                        traceId = EventLogContext.getTraceId(),
                        data = transferRequested,
                        aggregateId = split.targetEntityId,
                        parentEventId = eventId
                    )

                    val outboxEvent = OutboxEvent.createNew(
                        oeid = idGeneratorPort.nextPaymentId(),
                        eventType = transferEnvelope.eventType,
                        aggregateId = transferEnvelope.aggregateId,
                        payload = objectMapper.writeValueAsString(transferEnvelope)
                    )
                    outboxEvents.add(outboxEvent)
                }

                centralDbTransactionalFacadePort.saveAtomically(
                    payment = null,
                    tx = null,
                    journalEntries = emptyList(),
                    outboxEvents = outboxEvents
                )
                logger.info("💾 Internal split transfer outbox events persisted successfully for \${payment.splits.size} sub-sellers")

                dedupe.markProcessed(eventId, 3600)
                consumer.commitSync()
            } catch (e: Exception) {
                logger.error("❌ Failed to process MarketPlace splits for paymentIntentId: \${event.publicPaymentIntentId}", e)
                throw e
            }
        }
    }
}
