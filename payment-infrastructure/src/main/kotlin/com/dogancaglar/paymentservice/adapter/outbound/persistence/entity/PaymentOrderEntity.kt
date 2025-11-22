package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import java.time.Instant

data class PaymentOrderEntity(
    val paymentOrderId: Long,
    val paymentId: Long,
    val sellerId: String,
    val amountValue: Long,
    val amountCurrency: String,
    val status: PaymentOrderStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retryCount: Int,
)