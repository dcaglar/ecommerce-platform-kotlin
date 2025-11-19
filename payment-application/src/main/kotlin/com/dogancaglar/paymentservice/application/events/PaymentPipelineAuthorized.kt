package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentPipelineAuthorized private @JsonCreator constructor(
    @JsonProperty("paymentId") val paymentId: String,
    @JsonProperty("publicPaymentId") val publicPaymentId: String,
    @JsonProperty("buyerId") val buyerId: String,
    @JsonProperty("orderId") val orderId: String,
    @JsonProperty("totalAmountValue") val totalAmountValue: Long,
    @JsonProperty("currency") val currency: String,
    @JsonProperty("paymentLines") val paymentLines: List<PaymentAuthorizedLine>,
    @JsonProperty("authorizedAt") override val timestamp: LocalDateTime = LocalDateTime.now(),
) : com.dogancaglar.common.event.Event {

    override val eventType: String
        get() = "PAYMENT_AUTHORIZED"

    override fun deterministicEventId(): String =
        "$publicPaymentId:authorized"

    companion object {
        fun create(
            paymentId: String,
            buyerId: String,
            orderId: String,
            totalAmountValue: Long,
            currency: String,
            paymentLines: List<PaymentAuthorizedLine>,
            status: String = "AUTHORIZED",
            authorizedAt: LocalDateTime,
        ): PaymentPipelineAuthorized {
            require(paymentLines.isNotEmpty())
            require(paymentLines.all { it.currency == currency })

            return PaymentPipelineAuthorized(
                paymentId = paymentId,
                publicPaymentId = PaymentId(paymentId.toLong()).toPublicPaymentId(),
                buyerId = buyerId,
                orderId = orderId,
                totalAmountValue = totalAmountValue,
                currency = currency,
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