package com.dogancaglar.paymentservice.application.event

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentOrderSucceeded(
    @JsonProperty("paymentOrderId") override val paymentOrderId: String,
    @JsonProperty("publicPaymentOrderId") override val publicPaymentOrderId: String,
    @JsonProperty("paymentId") override val paymentId: String,
    @JsonProperty("publicPaymentId") override val publicPaymentId: String,
    @JsonProperty("sellerId") override val sellerId: String,
    @JsonProperty("amountValue") override val amountValue: BigDecimal,
    @JsonProperty("currency") override val currency: String,
    // Below are default values for the interface, not used by this event
    override val status: String = "SUCCESS",
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val updatedAt: LocalDateTime = LocalDateTime.now(),
    override val retryCount: Int = 0,
    override val retryReason: String? = null,
    override val lastErrorMessage: String? = null
) : PaymentOrderEvent