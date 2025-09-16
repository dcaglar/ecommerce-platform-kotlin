package com.dogancaglar.paymentservice.domain.event

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderPspResultUpdated @JsonCreator constructor(
    @JsonProperty("paymentOrderId") override val paymentOrderId: String,
    @JsonProperty("publicPaymentOrderId") override val publicPaymentOrderId: String,
    @JsonProperty("paymentId") override val paymentId: String,
    @JsonProperty("publicPaymentId") override val publicPaymentId: String,
    @JsonProperty("sellerId") override val sellerId: String,
    @JsonProperty("amountValue") override val amountValue: BigDecimal,
    @JsonProperty("currency") override val currency: String,
    @JsonProperty("status") override val status: String,            // last known domain status (from request)
    @JsonProperty("createdAt") override val createdAt: LocalDateTime,
    @JsonProperty("updatedAt") override val updatedAt: LocalDateTime,
    @JsonProperty("retryCount") override val retryCount: Int,       // attempt index (0..n)
    @JsonProperty("retryReason") override val retryReason: String? = null,
    @JsonProperty("lastErrorMessage") override val lastErrorMessage: String? = null,

    // PSP result payload (minimal, enough for Applier to decide)
    @JsonProperty("pspStatus") val pspStatus: String,               // maps to PaymentOrderStatus via PSPStatusMapper
    @JsonProperty("pspErrorCode") val pspErrorCode: String? = null,
    @JsonProperty("pspErrorDetail") val pspErrorDetail: String? = null,
    @JsonProperty("latencyMs") val latencyMs: Long? = null
) : PaymentOrderEvent