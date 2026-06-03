package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.paymentservice.application.util.toPublicPaymentIntentId
import com.dogancaglar.paymentservice.domain.model.payment.Payment
import com.dogancaglar.paymentservice.domain.model.payment.PaymentStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentCaptured private constructor(
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
        const val EVENT_TYPE = "payment_captured"

        fun from(payment: Payment, now: Instant): PaymentCaptured {
            require(payment.status == PaymentStatus.CAPTURED)
            return PaymentCaptured(
                paymentIntentId = payment.paymentIntentId.value.toString(),
                publicPaymentIntentId = payment.paymentIntentId.toPublicPaymentIntentId(),
                merchantAccountId = payment.merchantAccountId,
                amountValue = payment.capturedAmount.quantity,
                currency = payment.capturedAmount.currency.currencyCode,
                timestamp = now
            )
        }
        @JsonCreator
        internal fun fromJson(
            @JsonProperty("paymentIntentId") pId: String,
            @JsonProperty("publicPaymentIntentId") pubPId: String,
            @JsonProperty("merchantAccountId") merchantAccountId: String,
            @JsonProperty("amountValue") amount: Long,
            @JsonProperty("currency") currency: String,
            @JsonProperty("timestamp") timestamp: Instant
        ) = PaymentCaptured(
            pId, pubPId, merchantAccountId, amount, currency, timestamp
        )
    }
}
