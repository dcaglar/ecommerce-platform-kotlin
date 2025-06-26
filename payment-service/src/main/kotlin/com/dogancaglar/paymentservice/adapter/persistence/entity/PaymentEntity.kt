package com.dogancaglar.paymentservice.adapter.persistence.entity

import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime

class PaymentEntity(
    val paymentId: Long,
    val publicPaymentId: String,
    val buyerId: String,
    val orderId: String,
    val amountValue: BigDecimal,
    val amountCurrency: String,
    val status: PaymentStatus,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime? = null,
    val retryCount: Int? = null,
    val retryReason: String? = null,
    val lastErrorMessage: String? = null
) {
    constructor(paymentId: Long, publicPaymentId: String) : this(
        paymentId = paymentId,
        publicPaymentId = publicPaymentId,
        buyerId = "",
        orderId = "",
        amountValue = BigDecimal.ZERO,
        amountCurrency = "EUR",
        status = PaymentStatus.INITIATED,
        createdAt = LocalDateTime.now(),
        updatedAt = null,
        retryCount = null,
        retryReason = null,
        lastErrorMessage = null
    )
}