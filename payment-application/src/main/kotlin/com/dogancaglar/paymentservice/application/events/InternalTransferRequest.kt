package com.dogancaglar.paymentservice.application.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class InternalTransferRequest private constructor(
    override val paymentIntentId: String,
    override val publicPaymentIntentId: String,
    val sourceAccountId: String,
    val targetAccountId: String,
    override val amountValue: Long,
    override val currency: String,
    override val timestamp: Instant
) : com.dogancaglar.paymentservice.application.events.PaymentBaseEvent() {

    override val eventType = EVENT_TYPE

    override fun deterministicEventId(): String =
        "$publicPaymentIntentId:$targetAccountId:$eventType"

    companion object {
        const val EVENT_TYPE = "internal_transfer_request"

        fun from(
            paymentIntentId: String,
            publicPaymentIntentId: String,
            sourceAccountId: String,
            targetAccountId: String,
            amountValue: Long,
            currency: String,
            now: Instant
        ): InternalTransferRequest {
            return InternalTransferRequest(
                paymentIntentId = paymentIntentId,
                publicPaymentIntentId = publicPaymentIntentId,
                sourceAccountId = sourceAccountId,
                targetAccountId = targetAccountId,
                amountValue = amountValue,
                currency = currency,
                timestamp = now
            )
        }

        @JsonCreator
        internal fun fromJson(
            @JsonProperty("paymentIntentId") pId: String,
            @JsonProperty("publicPaymentIntentId") pubPId: String,
            @JsonProperty("sourceAccountId") sourceAccountId: String,
            @JsonProperty("targetAccountId") targetAccountId: String,
            @JsonProperty("amountValue") amount: Long,
            @JsonProperty("currency") currency: String,
            @JsonProperty("timestamp") timestamp: Instant
        ) = InternalTransferRequest(
            pId, pubPId, sourceAccountId, targetAccountId, amount, currency, timestamp
        )
    }
}
