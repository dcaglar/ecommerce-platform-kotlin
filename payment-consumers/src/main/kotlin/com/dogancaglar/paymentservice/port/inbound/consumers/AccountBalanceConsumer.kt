package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.logging.GenericLogFields.JOURNAL_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ORDER_ID
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.util.LedgerDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Batch consumer for updating account balances from ledger entries.
 * 
 * Implements:
 * - Batch processing (100-500 events)
 * - Idempotency checks (Redis SET for ledgerEntryIds)
 * - Atomic offset commit via KafkaTxExecutor
 * - Redis delta updates (TTL-based cleanup)
 */
@Component
class AccountBalanceConsumer(
    @param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
    private val accountBalanceService: AccountBalanceUseCase,
    private val dedupe: EventDeduplicationPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    

    @KafkaListener(
        topics = [Topics.LEDGER_ENTRIES_RECORDED],
        containerFactory = "${Topics.LEDGER_ENTRIES_RECORDED}-factory",
        groupId = CONSUMER_GROUPS.ACCOUNT_BALANCE_CONSUMER
    )
    fun onLedgerEntriesRecorded(
        records: List<ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
            // Extract offsets for ALL records in batch
            val offsets = records.groupBy { 
                TopicPartition(it.topic(), it.partition()) 
            }.mapValues { (_, recordsForPartition) ->
                // Offset = highest offset in partition + 1
                val maxOffset = recordsForPartition.maxOf { it.offset() }
                OffsetAndMetadata(maxOffset + 1)
            }
            val groupMeta = consumer.groupMetadata()

                // Wrap in KafkaTxExecutor for atomic offset commit
                kafkaTx.run(offsets, groupMeta) {
                    // Deduplication: Filter out already-processed events and log duplicates
                    val newRecords = records.filter { record ->
                        val exists = dedupe.exists(record.value().eventId)
                        if (exists) {
                            logger.warn(
                                "⚠️ Event is processed already, skipping eventId=${record.value().eventId}, aggregateId=${record.value().aggregateId}"
                            )
                        }
                        !exists
                    }
                    
                    // Extract all ledger entry domain from batch
                    val allLedgerEntriesDomain = newRecords
                        .flatMap { it.value().data.ledgerEntries }
                        .map {
                            logger.info(
                                "🎬 Processing  journal ${it.journalType.name} with journal entry id ${it.journalEntryId}  ledger entry id ${it.ledgerEntryId} ")
                            LedgerDomainEventMapper.toDomain(it) }
                    // idempotenct update Process batch with idempotency check
                    accountBalanceService.updateAccountBalancesBatch(allLedgerEntriesDomain)
                    
                    // Mark processed events
                    newRecords.forEach { dedupe.markProcessed(it.value().eventId, 3600) }
                }


    }
}

