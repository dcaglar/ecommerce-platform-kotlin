package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
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
        const val EVENT_TYPE = "payment_order_captured"

        fun from(order: PaymentOrder, now: Instant): PaymentCaptured {
            require(order.status == PaymentOrderStatus.CAPTURED)
            return PaymentCaptured(
                paymentIntentId = order.paymentId.value.toString(),
                publicPaymentIntentId = order.paymentId.toPublicPaymentId(),
                merchantAccountId = order.sellerId.value,
                amountValue = order.amount.quantity,
                currency = order.amount.currency.currencyCode,
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
