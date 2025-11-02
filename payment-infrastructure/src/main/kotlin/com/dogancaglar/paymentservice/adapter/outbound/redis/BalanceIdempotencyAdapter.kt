package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.paymentservice.ports.outbound.BalanceIdempotencyPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Redis adapter for tracking processed ledger entry IDs (idempotency).
 * Uses SET operations with TTL for automatic cleanup.
 */
@Component
class BalanceIdempotencyAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${account-balance.idempotency-ttl-seconds:86400}") // 24 hours
    private val idempotencyTtlSeconds: Long
) : BalanceIdempotencyPort {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    private val prefix = "balance:processed:"
    
    override fun areLedgerEntryIdsProcessed(ledgerEntryIds: List<Long>): Boolean {
        if (ledgerEntryIds.isEmpty()) return false
        
        // Check keys one by one (stop on first match for efficiency)
        // In practice, most batches won't have duplicates, so early exit is fast
        for (id in ledgerEntryIds) {
            val key = "$prefix$id"
            val exists = redisTemplate.hasKey(key)
            if (exists) {
                logger.debug("Found already processed ledger entry ID: {}", id)
                return true
            }
        }
        
        return false
    }
    
    override fun markLedgerEntryIdsProcessed(ledgerEntryIds: List<Long>) {
        if (ledgerEntryIds.isEmpty()) return
        
        ledgerEntryIds.forEach { id ->
            val key = "$prefix$id"
            // SET if not exists (idempotent) - safe to call multiple times
            redisTemplate.opsForValue().setIfAbsent(
                key, 
                "1", 
                idempotencyTtlSeconds, 
                TimeUnit.SECONDS
            )
        }
        
        logger.debug("Marked {} ledger entry IDs as processed", ledgerEntryIds.size)
    }
}

