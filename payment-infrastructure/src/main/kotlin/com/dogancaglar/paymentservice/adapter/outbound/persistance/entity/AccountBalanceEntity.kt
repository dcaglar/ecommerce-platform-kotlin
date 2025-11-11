package com.dogancaglar.paymentservice.adapter.outbound.persistance.entity

import java.time.LocalDateTime

/**
 * Entity representing an account balance snapshot in the database.
 */
data class AccountBalanceEntity(
    val accountCode: String, // Primary key: e.g., "MERCHANT_PAYABLE.MERCHANT-456"
    val balance: Long,
    val lastAppliedEntryId:Long,
    val lastSnapshotAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

