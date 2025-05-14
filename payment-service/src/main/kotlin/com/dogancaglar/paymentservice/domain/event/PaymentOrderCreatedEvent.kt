package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaymentOrderCreatedEvent @JsonCreator constructor(
    val paymentOrderId: String = "",
    val paymentId: String = "",
    val sellerId: String = "",
    val amountValue: BigDecimal = BigDecimal.ZERO,
    val currency: String = "",
    val status : String = "",
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = LocalDateTime.now(),
    val retryCount : Int

)

fun PaymentOrderCreatedEvent.toDomain(): PaymentOrder {
    return PaymentOrder(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amount = Amount(this.amountValue, this.currency),
        status = PaymentOrderStatus.INITIATED,
        createdAt =this.createdAt,
        updatedAt = LocalDateTime.now(),
        retryCount = this.retryCount
    )
}





