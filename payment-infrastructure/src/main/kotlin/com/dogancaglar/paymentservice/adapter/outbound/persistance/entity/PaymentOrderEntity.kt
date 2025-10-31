package com.dogancaglar.paymentservice.adapter.outbound.persistance.entity

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import java.time.LocalDateTime

 class PaymentOrderEntity internal constructor(
    val paymentOrderId: Long,
    val publicPaymentOrderId: String,
    val paymentId: Long,
    val publicPaymentId: String,
    val sellerId: String,
    val amountValue: Long,
    val amountCurrency: String,
    val status: PaymentOrderStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val retryCount: Int = 0,
    val retryReason: String? = null,
    val lastErrorMessage: String? = null
)