package com.dogancaglar.common.db.entity

import java.time.LocalDateTime

data class TransferEntity(
    val transferId: Long,
    val sourceTransactionId: Long,
    val amountValue: Long,
    val currency: String,
    val reversedAmountValue: Long,
    val targetAccountType: String,
    val targetEntityId: String,
    val sourceAccountType: String,
    val sourceEntityId: String,
    val status: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
