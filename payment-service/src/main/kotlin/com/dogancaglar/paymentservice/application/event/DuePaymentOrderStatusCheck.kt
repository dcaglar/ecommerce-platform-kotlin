package com.dogancaglar.paymentservice.application.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class DuePaymentOrderStatusCheck constructor(
    @JsonProperty("paymentOrderId") val paymentOrderId: String,
    @JsonProperty("publicPaymentOrderId") val publicPaymentOrderId: String,
    @JsonProperty("paymentId") val paymentId: String,
    @JsonProperty("publicPaymentId") val publicPaymentId: String,
    @JsonProperty("sellerId") val sellerId: String,
    @JsonProperty("amountValue") val amountValue: BigDecimal,
    @JsonProperty("currency") val currency: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("createdAt") val createdAt: LocalDateTime = LocalDateTime.now(),
    @JsonProperty("updatedAt") val updatedAt: LocalDateTime = LocalDateTime.now(),
    @JsonProperty("retryCount") val retryCount: Int,
    @JsonProperty("retryReason") val retryReason: String? = null,
    @JsonProperty("lastErrorMessage") val retrylastErrorMessageReason: String? = null,
)


