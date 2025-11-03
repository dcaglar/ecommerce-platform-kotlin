package com.dogancaglar.paymentservice.util

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.LedgerEntriesRecorded
import org.apache.kafka.clients.consumer.ConsumerRecord

/**
 * Minimal Kafka test helper for wrapping LedgerEntriesRecorded envelopes
 * into ConsumerRecord objects.
 *
 * Keeps AccountBalanceConsumer and other Kafka-related tests simple
 * and deterministic without requiring a running Kafka broker.
 */
object KafkaRecordTestHelper {

    fun createLedgerEntriesRecordedRecord(
        topic: String = "ledger_entries_recorded",
        partition: Int = 0,
        offset: Long = 0L,
        key: String = "SELLER-TEST",
        envelope: EventEnvelope<LedgerEntriesRecorded>
    ): ConsumerRecord<String, EventEnvelope<LedgerEntriesRecorded>> {
        return ConsumerRecord(
            topic,
            partition,
            offset,
            key,
            envelope
        )
    }
}