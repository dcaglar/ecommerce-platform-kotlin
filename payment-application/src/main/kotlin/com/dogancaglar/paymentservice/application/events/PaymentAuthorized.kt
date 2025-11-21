package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentAuthorized private @JsonCreator constructor(
    @JsonProperty("paymentId") val paymentId: String,
    @JsonProperty("publicPaymentId") val publicPaymentId: String,
    @JsonProperty("buyerId") val buyerId: String,
    @JsonProperty("orderId") val orderId: String,
    @JsonProperty("totalAmountValue") val totalAmountValue: Long,
    @JsonProperty("currency") val currency: String,
    @JsonProperty("paymentLines") val paymentLines: List<PaymentAuthorizedLine>,
    @JsonProperty("authorizedAt") override val timestamp: Instant = Utc.nowInstant(),
) : Event {

    override val eventType: String
        get() = "payment_authorized"

    override fun deterministicEventId(): String =
        "$publicPaymentId:authorized"

    companion object {
        fun from(
            payment: Payment,
            paymentLines: List<PaymentAuthorizedLine>,
            authorizedAt: Instant,
        ): PaymentAuthorized {
            require(paymentLines.isNotEmpty())
            require(paymentLines.all { it.currency == payment.totalAmount.currency.currencyCode })
            require(payment.status == PaymentStatus.AUTHORIZED){
                "PaymentAuthorized can only be created if payment status was authorized, but was ${payment.status}"
            }
            return PaymentAuthorized(
                paymentId = payment.paymentId.value.toString(),
                publicPaymentId = payment.paymentId.toPublicPaymentId(),
                buyerId = payment.buyerId.value,
                orderId = payment.orderId.value,
                totalAmountValue = payment.totalAmount.quantity,
                currency = payment.totalAmount.currency.currencyCode,
                paymentLines = paymentLines,
                timestamp = authorizedAt,
            )
        }
    }
}

/**
 * Value object representing a per-seller authorized line.
 */
data class PaymentAuthorizedLine @JsonCreator private constructor(
    @JsonProperty("sellerId") val sellerId: String,
    @JsonProperty("amountValue") val amountValue: Long,
    @JsonProperty("currency") val currency: String
) {
    companion object {
        fun of(sellerId: String, amountValue: Long, currency: String): PaymentAuthorizedLine {
            require(sellerId.isNotBlank()) { "sellerId must not be blank" }
            require(amountValue > 0) { "amountValue must be positive" }
            require(currency.matches(Regex("^[A-Z]{3}$"))) { "currency must be ISO 4217 code" }
            return PaymentAuthorizedLine(sellerId, amountValue, currency)
        }
    }
}