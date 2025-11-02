package com.dogancaglar.paymentservice.ports.outbound

/**
 * Port for updating account balance deltas in cache (Redis).
 */
interface AccountBalanceCachePort {
    /**
     * Increments the balance delta for an account.
     * 
     * @param accountId Format: "{accountCode}:{sellerId}:{currency}" 
     *                  e.g., "MERCHANT_ACCOUNT.MERCHANT-456:USD"
     * @param delta The amount to increment (can be positive or negative)
     */
    fun incrementDelta(accountId: String, delta: Long)
    
    /**
     * Gets the current delta for an account (or 0 if not exists/expired).
     */
    fun getDelta(accountId: String): Long
    
    /**
     * Calculates real-time balance: snapshotBalance + delta.
     */
    fun getRealTimeBalance(accountId: String, snapshotBalance: Long): Long
}

