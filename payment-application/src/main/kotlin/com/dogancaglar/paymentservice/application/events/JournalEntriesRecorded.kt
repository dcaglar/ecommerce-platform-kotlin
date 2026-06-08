package com.dogancaglar.paymentservice.application.events

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class JournalEntriesRecorded(
    override val paymentIntentId: String,
    override val publicPaymentIntentId: String,
    val sellerId: String?,
    override val amountValue: Long,
    override val currency: String,
    val ledgerBatchId: String,
    val ledgerEntries: List<JournalEntryEventData>,
    override val timestamp: Instant
) : PaymentBaseEvent(paymentIntentId, publicPaymentIntentId, amountValue, currency, timestamp) {

    override val eventType: String = EVENT_TYPE

    // deterministicEventId() inherited from base: "$publicPaymentIntentId:$eventType"

    companion object {
        const val EVENT_TYPE = EventType.JOURNAL_ENTRIES_RECORDED

        /**
         * Factory for use by RecordLedgerEntriesService.
         */
        fun from(
            cmd: PaymentBaseEvent,
            batchId: String,
            entries: List<JournalEntryEventData>,
            now: Instant
        ) = JournalEntriesRecorded(
            paymentIntentId = cmd.paymentIntentId,
            publicPaymentIntentId = cmd.publicPaymentIntentId,
            sellerId = if (cmd is CaptureConfirmed) cmd.merchantAccountId else null,
            amountValue = cmd.amountValue,
            currency = cmd.currency,
            ledgerBatchId = batchId,
            ledgerEntries = entries,
            timestamp = now
        )
    }
}