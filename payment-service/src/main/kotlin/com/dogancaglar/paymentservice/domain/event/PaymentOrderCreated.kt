package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderCreated @JsonCreator constructor(
    @JsonProperty("paymentOrderId") val paymentOrderId: String,
    @JsonProperty("paymentId") val paymentId: String,
    @JsonProperty("sellerId") val sellerId: String,
    @JsonProperty("amountValue") val amountValue: BigDecimal,
    @JsonProperty("currency") val currency: String,
    @JsonProperty("status") val status: String,
    @JsonProperty("createdAt") val createdAt: LocalDateTime= LocalDateTime.now(),
    @JsonProperty("updatedAt") val updatedAt: LocalDateTime= LocalDateTime.now(),
    @JsonProperty("retryCount") val retryCount: Int

)

fun PaymentOrderCreated.toDomain(): PaymentOrder {
    return PaymentOrder(
        paymentOrderId = this.paymentOrderId!!,
        paymentId = this.paymentId!!,
        sellerId = this.sellerId,
        amount = Amount(this.amountValue, this.currency),
        status = PaymentOrderStatus.INITIATED,
        createdAt =this.createdAt,
        updatedAt = LocalDateTime.now(),
        retryCount = this.retryCount
    )
}





