package com.dogancaglar.paymentservice.application.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty


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