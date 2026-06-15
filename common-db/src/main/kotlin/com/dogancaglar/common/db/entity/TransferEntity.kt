package com.dogancaglar.common.db.entity

import java.time.LocalDateTime

data class TransferEntity(
    val transferId: Long,
    val sourceTransactionId: Long,
    val amountValue: Long,
    val currency: String,
    val targetAccount: String,
    val sourceAccount: String,
    val transferType: String,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
