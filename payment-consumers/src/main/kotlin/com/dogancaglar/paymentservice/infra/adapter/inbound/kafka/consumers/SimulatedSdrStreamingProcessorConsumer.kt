package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.JournalEntriesRecorded
import com.dogancaglar.paymentservice.application.events.SettlementReceived
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxWriterPort
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.dogancaglar.paymentservice.ports.outbound.PspSimulationRulesPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
@Profile("test", "local","azure")
class SimulatedSdrStreamingProcessorConsumer(
    private val pspSimulationRulesPort: PspSimulationRulesPort,
    private val centralOutboxWriterPort: CentralOutboxWriterPort,
    private val idGeneratorPort: IdGeneratorPort,
    private val serializationPort: SerializationPort,
    private val dedupe: EventDeduplicationPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.JOURNAL_ENTRIES_RECORDED],
        containerFactory = CONSUMER_GROUPS.SETTLEMENT_RECORD_SIMULATOR + "-factory",
        groupId = CONSUMER_GROUPS.SETTLEMENT_RECORD_SIMULATOR
    )
    fun onInternalTransferJournalRecorded(record: ConsumerRecord<String, EventEnvelope<JournalEntriesRecorded>>) {
        val envelope = record.value()
        val ledgerEvent = envelope.data

        val firstEntry = ledgerEvent.ledgerEntries.firstOrNull()
        val groupUniqueDeterministicEventId = ledgerEvent.deterministicEventId()
        EventLogContext.with(envelope) {
        if (dedupe.exists(groupUniqueDeterministicEventId)) {
            logger.warn("⚠️ Event is processed already, skipping eventId=\$groupUniqueDeterministicEventId")
            return@with
        }
        //only when
        // 🟢 REALIGNED FILTER: We trigger the SDR bank line simulation ONLY when
        // the internal transfer clearing allocations have officially hit the ledger books!
        if (firstEntry?.journalType  != JournalType.INTERNAL_TRANSFER) return@with
        val merchantAccount = ledgerEvent.customPartitionKey ?: return@with
        if (!pspSimulationRulesPort.isSimulationTarget(merchantAccount)) return@with


            logger.debug("Reactive SDR Matcher captured a CAPTURE journal event for simulated merchant=\$merchantAccount. Synthesizing batch clearing line item.")

            // Leverage structural push-driven data extraction out of the event envelope map arrays
            val grossAmountValue = ledgerEvent.amountValue
            val currency = ledgerEvent.currency

            // Compute standard network overhead fees (1.5% processing baseline fee reduction)
            val feeAmountValue = (grossAmountValue * 0.015).toLong().coerceAtLeast(1L)
            val netCashAmountValue = grossAmountValue - feeAmountValue

            val settlementLineEvent = SettlementReceived(
                paymentIntentId = ledgerEvent.paymentIntentId,
                publicPaymentIntentId = ledgerEvent.publicPaymentIntentId,
                merchantAccount = merchantAccount,
                grossAmountValue = grossAmountValue,
                netCashAmountValue = netCashAmountValue,
                pspFeeAmountValue = feeAmountValue,
                currency = currency
            )

            val sdrEnvelope = EventEnvelopeFactory.envelopeFor(
                traceId = EventLogContext.getTraceId(),
                data = settlementLineEvent,
                aggregateId = ledgerEvent.publicPaymentIntentId,
                parentEventId = envelope.eventId
            )

            val outboxRecord = OutboxEvent.createNew(
                oeid = idGeneratorPort.generateId(),
                eventType = "settlement_received", // Dictates conversion mappings down inside the relay job
                aggregateId = ledgerEvent.publicPaymentIntentId,
                payload = serializationPort.toJson(sdrEnvelope)
            )

            centralOutboxWriterPort.save(outboxRecord)
            logger.debug("Successfully queued synthetic SDR settlement loopback event into central outbox for paymentId=\${ledgerEvent.paymentIntentId}")
            logger.info("Simulated SDR streaming processor consumer executed successfully for paymentId=${ledgerEvent.paymentIntentId}")
        }
    }
}
