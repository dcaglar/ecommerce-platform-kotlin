package com.dogancaglar.paymentservice.application.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import com.dogancaglar.paymentservice.application.events.PaymentBaseEvent

@JsonIgnoreProperties(ignoreUnknown = true)
data class LedgerEntriesRecorded private constructor(
    @JsonProperty("paymentIntentId")       override val paymentIntentId: String,
    @JsonProperty("publicPaymentIntentId")  override val publicPaymentIntentId: String,
    @JsonProperty("sellerId") val sellerId: String?,
    @JsonProperty("amountValue") override val amountValue: Long,
    @JsonProperty("currency") override val currency: String,

    @JsonProperty("ledgerBatchId") val ledgerBatchId: String,
    @JsonProperty("ledgerEntries") val ledgerEntries: List<LedgerEntryEventData>,

    @JsonProperty("timestamp") override val timestamp: Instant
) : PaymentBaseEvent() {

    override val eventType: String = EVENT_TYPE

    /**
     * Deterministic ID uses *ledgerBatchId*.
     * Why?
     * - A batch is the true atomic ledger mutation.
     * - Replays / retries produce the same batchId → idempotency safe.
     */
    override fun deterministicEventId(): String =
        "$ledgerBatchId:$eventType"

    companion object {
        const val EVENT_TYPE = "ledger_entries_recorded"

        /**
         * Factory for use by RecordLedgerEntriesService.
         */
        fun from(
            cmd: PaymentBaseEvent,
            batchId: String,
            entries: List<LedgerEntryEventData>,
            now: Instant
        ) = LedgerEntriesRecorded(
            paymentIntentId = cmd.paymentIntentId,
            publicPaymentIntentId = cmd.publicPaymentIntentId,
            sellerId = if (cmd is PaymentCaptured) cmd.merchantAccountId else null,
            amountValue = cmd.amountValue,
            currency = cmd.currency,
            ledgerBatchId = batchId,
            ledgerEntries = entries,
            timestamp = now
        )

        /**
         * Jackson-only constructor (required for Kafka consumers).
         */
        @JsonCreator
        internal fun fromJson(
            @JsonProperty("paymentIntentId") pId: String,
            @JsonProperty("publicPaymentIntentId") pubPId: String,
            @JsonProperty("sellerId") sellerId: String,
            @JsonProperty("amountValue") amountValue: Long,
            @JsonProperty("currency") currency: String,
            @JsonProperty("ledgerBatchId") batchId: String,
            @JsonProperty("ledgerEntries") ledgerEntries: List<LedgerEntryEventData>,
            @JsonProperty("timestamp") timestamp: Instant
        ) = LedgerEntriesRecorded(
            pId,
            pubPId,
            sellerId,
            amountValue,
            currency,
            batchId,
            ledgerEntries,
            timestamp
        )
    }
}