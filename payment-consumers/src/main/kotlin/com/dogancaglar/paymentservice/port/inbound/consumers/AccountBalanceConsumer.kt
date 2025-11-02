package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceUseCase
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
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
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    private val eventsProcessedCounter = meterRegistry.counter("account_balance_events_total")
    private val eventsSkippedCounter = meterRegistry.counter("account_balance_events_skipped_total")
    private val batchProcessingTimer = meterRegistry.timer("account_balance_batch_processing_seconds")
    
    @KafkaListener(
        topics = [Topics.LEDGER_ENTRIES_RECORDED],
        containerFactory = "${Topics.LEDGER_ENTRIES_RECORDED}-factory",
        groupId = CONSUMER_GROUPS.ACCOUNT_BALANCE_CONSUMER
    )
    fun onLedgerEntriesRecorded(
        records: List<ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        if (records.isEmpty()) return
        
        val timerSample = Timer.start(meterRegistry)
        
        try {
            // Extract offsets for ALL records in batch
            val offsets = records.groupBy { 
                TopicPartition(it.topic(), it.partition()) 
            }.mapValues { (_, recordsForPartition) ->
                // Offset = highest offset in partition + 1
                val maxOffset = recordsForPartition.maxOf { it.offset() }
                OffsetAndMetadata(maxOffset + 1)
            }
            
            val groupMeta = consumer.groupMetadata()
            
            // Use first event's traceId for MDC (all events in batch share partition, so likely same sellerId)
            val firstEnvelope = records.first().value()
            
            LogContext.with(firstEnvelope) {
                // Wrap in KafkaTxExecutor for atomic offset commit
                kafkaTx.run(offsets, groupMeta) {
                    logger.info(
                        "💰 Processing batch of {} ledger entry events (traceId={})",
                        records.size,
                        firstEnvelope.traceId
                    )
                    
                    // Extract all ledger entries from batch
                    val allLedgerEntries = records.flatMap { it.value().data.ledgerEntries }
                    
                    if (allLedgerEntries.isEmpty()) {
                        logger.debug("Batch contains no ledger entries, skipping")
                        eventsSkippedCounter.increment(records.size.toDouble())
                        return@run
                    }
                    
                    // Process batch with idempotency check
                    val processedIds = accountBalanceService.updateAccountBalancesBatch(allLedgerEntries)
                    
                    if (processedIds.isEmpty()) {
                        logger.debug("All ledger entries in batch were already processed (idempotent skip)")
                        eventsSkippedCounter.increment(records.size.toDouble())
                    } else {
                        logger.info(
                            "✅ Updated balances for {} ledger entries from {} events",
                            processedIds.size,
                            records.size
                        )
                        eventsProcessedCounter.increment(records.size.toDouble())
                    }
                    
                    // Offset commit happens atomically if no exception
                }
            }
        } catch (ex: Exception) {
            logger.error("❌ Batch processing failed, offsets NOT committed. Will retry.", ex)
            throw ex // Let Kafka retry the batch
        } finally {
            timerSample.stop(batchProcessingTimer)
        }
    }
}

