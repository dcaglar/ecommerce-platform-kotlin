package com.dogancaglar.paymentservice.application.commands

import com.dogancaglar.paymentservice.application.events.PaymentOrderCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class LedgerRecordingCommand private constructor(
    @JsonProperty("paymentOrderId") override val paymentOrderId: String,
    @JsonProperty("publicPaymentOrderId") override val publicPaymentOrderId: String,
    @JsonProperty("paymentId") override val paymentId: String,
    @JsonProperty("publicPaymentId") override val publicPaymentId: String,
    @JsonProperty("sellerId") override val sellerId: String,
    @JsonProperty("amountValue") override val amountValue: Long,
    @JsonProperty("currency") override val currency: String,

    /** "payment_order_succeeded" or "payment_order_failed" */
    @JsonProperty("finalStatus") val finalStatus: String,

    @JsonProperty("timestamp") override val timestamp: Instant
) : PaymentOrderCommand() {

    override val eventType: String = EVENT_TYPE

    /**
     * Ledger recording is per  FINAL status (success or fail)
     * Therefore deterministic ID MUST include finalStatus
     */
    override fun deterministicEventId(): String =
        "$publicPaymentOrderId:$eventType:$finalStatus"

    companion object {
        const val EVENT_TYPE = "ledger_recording_requested"

        /**
         * Factory for domain → command mapping
         * (the ONLY valid creator in production code)
         */
        fun from(final: PaymentOrderFinalized, now: Instant): LedgerRecordingCommand =
            LedgerRecordingCommand(
                paymentOrderId = final.paymentOrderId,
                publicPaymentOrderId = final.publicPaymentOrderId,
                paymentId = final.paymentId,
                publicPaymentId = final.publicPaymentId,
                sellerId = final.sellerId,
                amountValue = final.amountValue,
                currency = final.currency,
                finalStatus = final.status,     // e.g. "payment_order_succeeded"
                timestamp = now
            )

        /**
         * Jackson-only constructor for Kafka deserialization.
         * DO NOT CALL MANUALLY.
         */
        @JsonCreator
        internal fun fromJson(
            @JsonProperty("paymentOrderId") pOrderId: String,
            @JsonProperty("publicPaymentOrderId") pubOrderId: String,
            @JsonProperty("paymentId") pId: String,
            @JsonProperty("publicPaymentId") pubPId: String,
            @JsonProperty("sellerId") seller: String,
            @JsonProperty("amountValue") amount: Long,
            @JsonProperty("currency") currency: String,
            @JsonProperty("finalStatus") finalStatus: String,
            @JsonProperty("timestamp") timestamp: Instant
        ) = LedgerRecordingCommand(
            pOrderId,
            pubOrderId,
            pId,
            pubPId,
            seller,
            amount,
            currency,
            finalStatus,
            timestamp
        )
    }
}