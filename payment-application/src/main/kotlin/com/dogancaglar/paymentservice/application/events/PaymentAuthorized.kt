package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.events.PaymentEvent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentAuthorized private @JsonCreator constructor(
    @JsonProperty("paymentId") override val paymentId: String,
    @JsonProperty("publicPaymentId") override val publicPaymentId: String,
    @JsonProperty("buyerId") override val buyerId: String,
    @JsonProperty("orderId") override val orderId: String,
    @JsonProperty("totalAmountValue") override val totalAmountValue: Long,
    @JsonProperty("currency") override val currency: String,
    @JsonProperty("status") override val status: String,
    @JsonProperty("paymentLines") val paymentLines: List<PaymentAuthorizedLine>,
    @JsonProperty("createdAt") override val createdAt: LocalDateTime = LocalDateTime.now(),
    @JsonProperty("updatedAt") override val updatedAt: LocalDateTime = LocalDateTime.now()
) : PaymentEvent {

    companion object {
        fun create(
            paymentId: String,
            buyerId: String,
            orderId: String,
            totalAmountValue: Long,
            currency: String,
            paymentLines: List<PaymentAuthorizedLine>,
            status: String = "AUTHORIZED",
            createdAt: LocalDateTime = LocalDateTime.now(),
            updatedAt: LocalDateTime = LocalDateTime.now()
        ): PaymentAuthorized {
            require(paymentLines.isNotEmpty()) { "paymentLines must not be empty" }
            require(paymentLines.all { it.currency == currency }) {
                "All paymentLines must use the same currency ($currency)"
            }

            return PaymentAuthorized(
                paymentId = paymentId,
                publicPaymentId = PaymentId(paymentId.toLong()).toPublicPaymentId(),
                buyerId = buyerId,
                orderId = orderId,
                totalAmountValue = totalAmountValue,
                currency = currency,
                status = status,
                paymentLines = paymentLines,
                createdAt = createdAt,
                updatedAt = updatedAt
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