package com.dogancaglar.paymentservice.application.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import com.dogancaglar.paymentservice.application.events.PaymentBaseEvent

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalAsyncCaptureToPspPerformed private constructor(
    val captureTxId: Long,
    override val paymentIntentId: String,
    override val publicPaymentIntentId: String,
    val merchantAccountId: String,
    override val amountValue: Long,
    override val currency: String,
    override val timestamp: Instant
) : PaymentBaseEvent() {

    override val eventType = EVENT_TYPE

    override fun deterministicEventId(): String =
        "$publicPaymentIntentId:$eventType"

    companion object {
        const val EVENT_TYPE = "external_async_capture_psp_performed"

        fun from(
            captureTxId: Long,
            paymentIntentId: String,
            publicPaymentIntentId: String,
            merchantAccountId: String,
            amountValue: Long,
            currency: String,
            now: Instant
        ): ExternalAsyncCaptureToPspPerformed {
            return ExternalAsyncCaptureToPspPerformed(
                captureTxId = captureTxId,
                paymentIntentId = paymentIntentId,
                publicPaymentIntentId = publicPaymentIntentId,
                merchantAccountId = merchantAccountId,
                amountValue = amountValue,
                currency = currency,
                timestamp = now
            )
        }

        @JsonCreator
        internal fun fromJson(
            @JsonProperty("captureTxId") captureTxId: Long?,
            @JsonProperty("paymentIntentId") pId: String,
            @JsonProperty("publicPaymentIntentId") pubPId: String,
            @JsonProperty("merchantAccountId") merchantAccountId: String,
            @JsonProperty("amountValue") amount: Long,
            @JsonProperty("currency") currency: String,
            @JsonProperty("timestamp") timestamp: Instant
        ) = ExternalAsyncCaptureToPspPerformed(
            captureTxId ?: 0L, pId, pubPId, merchantAccountId, amount, currency, timestamp
        )
    }
}
