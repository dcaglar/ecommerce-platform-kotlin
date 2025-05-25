package com.dogancaglar.paymentservice.application.event

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class PaymentOrderSucceeded(
    @JsonProperty("paymentOrderId") val paymentOrderId: String,
    @JsonProperty("publicPaymentOrderId") val publicPaymentOrderId: String,
    @JsonProperty("paymenId") val paymenId: String,
    @JsonProperty("paymentId") val publicPaymentId: String,
    @JsonProperty("sellerId") val sellerId: String,
    @JsonProperty("amountValue") val amountValue: BigDecimal,
    @JsonProperty("currency") val currency: String,
)