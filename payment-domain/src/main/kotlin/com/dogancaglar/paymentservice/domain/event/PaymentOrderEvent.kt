package com.dogancaglar.paymentservice.domain.event

import java.time.LocalDateTime

interface PaymentOrderEvent {
    val paymentOrderId: String
    val publicPaymentOrderId: String
    val paymentId: String
    val publicPaymentId: String
    val sellerId: String
    val amountValue: Long
    val currency: String
    val status: String
    val createdAt: LocalDateTime
    val updatedAt: LocalDateTime
    val retryCount: Int
    val retryReason: String?
    val lastErrorMessage: String?
}