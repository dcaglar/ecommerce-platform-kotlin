package com.dogancaglar.paymentservice.application.commands

import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class LedgerRecordingAuthorizationCommand private constructor(
    @JsonProperty("paymentIntentId") override val paymentIntentId: String,
    @JsonProperty("publicPaymentIntentId") override val publicPaymentIntentId: String,
    @JsonProperty("paymentId") override val paymentId: String,
    @JsonProperty("publicPaymentId") override val publicPaymentId: String,
    @JsonProperty("amountValue") override val amountValue: Long,
    @JsonProperty("currency") override val currency: String,
    @JsonProperty("timestamp") override val timestamp: Instant
) : PaymentCommand() {

    override val eventType: String = EVENT_TYPE

    /**
     * Ledger recording is per  FINAL status (success or fail)
     * Therefore deterministic ID MUST include finalStatus
     */
    override fun deterministicEventId(): String =
        "$publicPaymentId:$eventType:authorized"

    companion object {
        const val EVENT_TYPE = "ledger_recording_authorization_requested"

        /**
         * Factory for domain → command mapping
         * (the ONLY valid creator in production code)
         */
        fun from(final: PaymentAuthorized, now: Instant): LedgerRecordingAuthorizationCommand =
            LedgerRecordingAuthorizationCommand(
                paymentIntentId = final.paymentIntentId,
                publicPaymentIntentId = final.publicPaymentIntentId,
                paymentId = final.paymentId,
                publicPaymentId = final.publicPaymentId,
                amountValue = final.totalAmountValue,
                currency = final.currency,
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
        ) = LedgerRecordingAuthorizationCommand(
            pOrderId,
            pubOrderId,
            pId,
            pubPId,
            amount,
            currency,
            timestamp
        )
    }
}