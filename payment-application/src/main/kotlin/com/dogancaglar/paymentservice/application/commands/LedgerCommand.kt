package com.dogancaglar.paymentservice.application.commands

import java.time.LocalDateTime

interface LedgerCommand {
    val paymentOrderId: String
    val paymentId: String
    val sellerId: String
    val amountValue: Long
    val currency: String
    val status: String
    val createdAt: LocalDateTime
    val updatedAt: LocalDateTime

}

