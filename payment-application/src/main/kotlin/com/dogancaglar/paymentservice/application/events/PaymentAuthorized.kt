package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentIntentId
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
        get() = "payment_authorized"

    override fun deterministicEventId(): String =
        "$publicPaymentId:payment_authorized"

    companion object {
        fun from(
            payment: Payment,
            timestamp: Instant
        ): PaymentAuthorized {
            require(payment.status == PaymentStatus.NOT_CAPTURED){
                "PaymentAuthorized can only be created if payment  status was authorized, but was ${payment.status}"
            }
            return PaymentAuthorized(
                paymentId = payment.paymentId.value.toString(),
                publicPaymentId = payment.paymentId.toPublicPaymentId(),
                paymentIntentId = payment.paymentIntentId.value.toString(),
                publicPaymentIntentId = payment.paymentIntentId.toPublicPaymentIntentId(),
                buyerId = payment.buyerId.value,
                orderId = payment.orderId.value,
                totalAmountValue = payment.totalAmount.quantity,
                currency = payment.totalAmount.currency.currencyCode,
                paymentLines =   payment.paymentOrderLines.map { PaymentIntentAuthorizedOrderLine.of(it.sellerId.value,it.amount.quantity,it.amount.currency.currencyCode)},
                timestamp = timestamp
            )
        }

        @JsonCreator
        internal fun fromJson(
            @JsonProperty("paymentId")  paymentId: String,
            @JsonProperty("publicPaymentId")  publicPaymentId: String,
            @JsonProperty("paymentIntentId")  paymentIntentId: String,
            @JsonProperty("publicPaymentIntentId")  publicPaymentIntentId: String,
            @JsonProperty("buyerId")  buyerId: String,
            @JsonProperty("orderId")  orderId: String,
            @JsonProperty("totalAmountValue")  totalAmountValue: Long,
            @JsonProperty("currency")  currency: String,
            @JsonProperty("paymentLines")  paymentLines: List<PaymentIntentAuthorizedOrderLine>,
            @JsonProperty("authorizedAt")   timestamp: Instant = Utc.nowInstant(),
        ) = PaymentAuthorized(
            paymentId=paymentId,
            publicPaymentId = publicPaymentId,
            paymentIntentId =paymentIntentId,
            publicPaymentIntentId = publicPaymentIntentId,
            buyerId = buyerId,
            orderId = orderId,
            totalAmountValue = totalAmountValue,
            currency = currency,
            paymentLines = paymentLines,
        )
    }
}