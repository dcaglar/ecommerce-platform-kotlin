package com.dogancaglar.paymentservice.ports.outbound

import java.time.LocalDateTime

/**
 * Represents a balance snapshot in the database.
 */
data class AccountBalanceSnapshot(
    val accountCode: String, // Primary key: e.g., "MERCHANT_ACCOUNT.MERCHANT-456"
    val balance: Long, // Current balance in minor currency units
    val lastSnapshotAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * Port for persisting account balance snapshots to database.
 */
interface AccountBalanceSnapshotPort {
    /**
     * Gets a snapshot for an account (or null if not exists).
     * @param accountCode The account code (e.g., "MERCHANT_ACCOUNT.MERCHANT-456")
     */
    fun getSnapshot(accountCode: String): AccountBalanceSnapshot?
    
    /**
     * Saves or updates a snapshot.
     */
    fun saveSnapshot(snapshot: AccountBalanceSnapshot)
    
    /**
     * Finds all snapshots.
     */
    fun findAllSnapshots(): List<AccountBalanceSnapshot>
}

