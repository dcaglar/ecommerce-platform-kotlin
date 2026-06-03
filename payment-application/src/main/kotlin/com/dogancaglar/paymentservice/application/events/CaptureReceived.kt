package com.dogancaglar.paymentservice.application.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import com.dogancaglar.paymentservice.application.events.PaymentBaseEvent

@JsonIgnoreProperties(ignoreUnknown = true)
data class CaptureReceived private constructor(
    val captureTxId: Long,
    override val paymentIntentId: String,
    override val publicPaymentIntentId: String,
    val merchantAccountId: String,
    override val amountValue: Long,
    override val currency: String,
    override val timestamp: Instant,
    val attempt: Int
) : PaymentBaseEvent() {

    override val eventType = EVENT_TYPE

    override fun deterministicEventId(): String =
        "$publicPaymentIntentId:$eventType"

    fun withIncrementedAttempt(): CaptureReceived {
        return this.copy(attempt = this.attempt + 1)
    }

    companion object {
        const val EVENT_TYPE = "capture_received"

        fun from(
            captureTxId: Long,
            paymentIntentId: String,
            publicPaymentIntentId: String,
            merchantAccountId: String,
            amountValue: Long,
            currency: String,
            now: Instant,
            attempt: Int = 1
        ): CaptureReceived {
            return CaptureReceived(
                captureTxId = captureTxId,
                paymentIntentId = paymentIntentId,
                publicPaymentIntentId = publicPaymentIntentId,
                merchantAccountId = merchantAccountId,
                amountValue = amountValue,
                currency = currency,
                timestamp = now,
                attempt = attempt
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
            @JsonProperty("timestamp") timestamp: Instant,
            @JsonProperty("attempt") attempt: Int?
        ) = CaptureReceived(
            captureTxId ?: 0L, pId, pubPId, merchantAccountId, amount, currency, timestamp, attempt ?: 1
        )
    }
}
