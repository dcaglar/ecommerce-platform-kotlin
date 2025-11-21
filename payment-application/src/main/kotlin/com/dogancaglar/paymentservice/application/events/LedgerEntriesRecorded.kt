package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.paymentservice.application.commands.LedgerRecordingCommand
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class LedgerEntriesRecorded private constructor(
    @JsonProperty("paymentOrderId") override val paymentOrderId: String,
    @JsonProperty("publicPaymentOrderId") override val publicPaymentOrderId: String,
    @JsonProperty("paymentId") override val paymentId: String,
    @JsonProperty("publicPaymentId") override val publicPaymentId: String,
    @JsonProperty("sellerId") override val sellerId: String,
    @JsonProperty("amountValue") override val amountValue: Long,
    @JsonProperty("currency") override val currency: String,

    @JsonProperty("ledgerBatchId") val ledgerBatchId: String,
    @JsonProperty("ledgerEntries") val ledgerEntries: List<LedgerEntryEventData>,

    @JsonProperty("timestamp") override val timestamp: Instant
) : PaymentOrderEvent() {

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
            cmd: LedgerRecordingCommand,
            batchId: String,
            entries: List<LedgerEntryEventData>,
            now: Instant
        ) = LedgerEntriesRecorded(
            paymentOrderId = cmd.paymentOrderId,
            publicPaymentOrderId = cmd.publicPaymentOrderId,
            paymentId = cmd.paymentId,
            publicPaymentId = cmd.publicPaymentId,
            sellerId = cmd.sellerId,
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
            @JsonProperty("paymentOrderId") pOrderId: String,
            @JsonProperty("publicPaymentOrderId") pubOrderId: String,
            @JsonProperty("paymentId") pId: String,
            @JsonProperty("publicPaymentId") pubPId: String,
            @JsonProperty("sellerId") sellerId: String,
            @JsonProperty("amountValue") amountValue: Long,
            @JsonProperty("currency") currency: String,
            @JsonProperty("ledgerBatchId") batchId: String,
            @JsonProperty("ledgerEntries") ledgerEntries: List<LedgerEntryEventData>,
            @JsonProperty("timestamp") timestamp: Instant
        ) = LedgerEntriesRecorded(
            pOrderId,
            pubOrderId,
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