package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class PspResultUpdate @JsonCreator constructor(
    @JsonProperty("paymentOrderId") val paymentOrderId: String,              // Used for idempotency everywhere
    @JsonProperty("pspStatus") val pspStatus: PaymentOrderStatus,
    @JsonProperty("pspStatus") val eventTime: Instant,
    @JsonProperty("errorCode") val errorCode: String? = null,
    @JsonProperty("errorMessage") val errorMessage: String? = null
)