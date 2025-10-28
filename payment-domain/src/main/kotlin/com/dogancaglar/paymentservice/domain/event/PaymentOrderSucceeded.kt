package com.dogancaglar.paymentservice.domain.event

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
@JsonIgnoreProperties(ignoreUnknown = true)

data class PaymentOrderSucceeded @JsonCreator constructor(
    @JsonProperty("paymentOrderId") override val paymentOrderId: String,
    @JsonProperty("publicPaymentOrderId") override val publicPaymentOrderId: String,
    @JsonProperty("paymentId") override val paymentId: String,
    @JsonProperty("publicPaymentId") override val publicPaymentId: String,
    @JsonProperty("sellerId") override val sellerId: String,
    @JsonProperty("amountValue") override val amountValue: Long,
    @JsonProperty("currency") override val currency: String,
    @JsonProperty("status") override val status: String,
    @JsonProperty("createdAt") override val createdAt: LocalDateTime = LocalDateTime.now(),
    @JsonProperty("updatedAt") override val updatedAt: LocalDateTime = LocalDateTime.now(),
    // Below are default values for the interface, not used by this event
    @JsonProperty("retryCount") override val retryCount: Int=0,       // attempt index (0..n)
    @JsonProperty("retryReason") override val retryReason: String? = null,
    @JsonProperty("lastErrorMessage") override val lastErrorMessage: String? = null,
) : PaymentOrderEvent