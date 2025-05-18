package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal
import java.time.LocalDateTime
@JsonIgnoreProperties(ignoreUnknown = true)
data class ScheduledPaymentOrderStatusRequest constructor(
    val paymentOrderId: String,
    val paymentId: String,
    val sellerId: String,
    val amountValue: BigDecimal,
    val currency: String,
    val retryCount: Int,
    val status : String,
    val createdAt: LocalDateTime,
    val updatedAt : LocalDateTime? = LocalDateTime.now(),
    val retryReason: String? = null,
    val lastErrorMessage: String? = null
)


fun PaymentOrder.toSchedulePaymentOrderStatusEvent(retryReason:String?="Unknown reason", lastErrorMessage:String?="no error message"): ScheduledPaymentOrderStatusRequest {
    return ScheduledPaymentOrderStatusRequest(
        paymentOrderId = this.paymentOrderId,
        paymentId = this.paymentId,
        sellerId = this.sellerId,
        amountValue = this.amount.value,
        currency = this.amount.currency,
        status = this.status.name,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        retryCount = this.retryCount,
        retryReason = retryReason,
        lastErrorMessage = lastErrorMessage
    )
}

