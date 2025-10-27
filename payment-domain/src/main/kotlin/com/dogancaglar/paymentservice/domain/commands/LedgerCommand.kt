package com.dogancaglar.paymentservice.domain.commands

import java.time.LocalDateTime

interface LedgerCommand {
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

}