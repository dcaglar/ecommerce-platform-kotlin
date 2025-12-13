package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.util.toPublicPaymentIntentId
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentIntentAuthorized private @JsonCreator constructor(
    @JsonProperty("paymentIntentId") val paymentIntentId: String,
    @JsonProperty("publicPaymentIntentId") val publicPaymentIntentId: String,
    @JsonProperty("buyerId") val buyerId: String,
    @JsonProperty("orderId") val orderId: String,
    @JsonProperty("totalAmountValue") val totalAmountValue: Long,
    @JsonProperty("currency") val currency: String,
    @JsonProperty("paymentLines") val paymentLines: List<PaymentIntentAuthorizedOrderLine>,
    @JsonProperty("authorizedAt") override val timestamp: Instant = Utc.nowInstant(),
) : Event {

    override val eventType: String
        get() = "payment_authorized_intent"

    override fun deterministicEventId(): String =
        "$publicPaymentIntentId:authorized_intent"

    companion object {
        fun from(
            paymentIntent: PaymentIntent,
            authorizedAt: Instant,
        ): PaymentIntentAuthorized {
            val paymentLines =paymentIntent.paymentOrderLines
            require(paymentIntent.paymentOrderLines.isNotEmpty())
            require(paymentLines.all { it.amount.currency == paymentIntent.totalAmount.currency})
            require(paymentIntent.status == PaymentIntentStatus.AUTHORIZED){
                "PaymentAuthorized can only be created if payment intent status was authorized, but was ${paymentIntent.status}"
            }
            return PaymentIntentAuthorized(
                paymentIntentId = paymentIntent.paymentIntentId.value.toString(),
                publicPaymentIntentId = paymentIntent.paymentIntentId.toPublicPaymentIntentId(),
                buyerId = paymentIntent.buyerId.value,
                orderId = paymentIntent.orderId.value,
                totalAmountValue = paymentIntent.totalAmount.quantity,
                currency = paymentIntent.totalAmount.currency.currencyCode,
                paymentLines =   paymentLines.map { PaymentIntentAuthorizedOrderLine.of(it.sellerId.value,it.amount.quantity,it.amount.currency.currencyCode)},
                timestamp = authorizedAt
            )
        }
    }
}

/**
 * Value object representing a per-seller authorized line.
 */
data class PaymentIntentAuthorizedOrderLine @JsonCreator private constructor(
    @JsonProperty("sellerId") val sellerId: String,
    @JsonProperty("amountValue") val amountValue: Long,
    @JsonProperty("currency") val currency: String
) {
    companion object {
        fun of(sellerId: String, amountValue: Long, currency: String): PaymentIntentAuthorizedOrderLine {
            require(sellerId.isNotBlank()) { "sellerId must not be blank" }
            require(amountValue > 0) { "amountValue must be positive" }
            require(currency.matches(Regex("^[A-Z]{3}$"))) { "currency must be ISO 4217 code" }
            return PaymentIntentAuthorizedOrderLine(sellerId, amountValue, currency)
        }
    }
}