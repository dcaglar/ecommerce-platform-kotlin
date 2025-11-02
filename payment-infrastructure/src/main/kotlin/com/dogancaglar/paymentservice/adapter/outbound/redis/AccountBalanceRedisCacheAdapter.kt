package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Redis adapter for account balance delta cache.
 * Uses HINCRBY for atomic delta updates with TTL for automatic cleanup.
 */
@Component
class AccountBalanceRedisCacheAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${account-balance.delta-ttl-seconds:300}") // 5 minutes
    private val deltaTtlSeconds: Long
) : AccountBalanceCachePort {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    private val prefix = "balance:delta:"
    
    override fun incrementDelta(accountId: String, delta: Long) {
        val key = "$prefix$accountId"
        
        // Atomic increment with TTL refresh
        redisTemplate.opsForValue().increment(key, delta)
        redisTemplate.expire(key, deltaTtlSeconds, TimeUnit.SECONDS)
        
        logger.debug("Incremented delta for account {} by {}", accountId, delta)
    }
    
    override fun getDelta(accountId: String): Long {
        val key = "$prefix$accountId"
        val value = redisTemplate.opsForValue().get(key)
        return value?.toLong() ?: 0L
    }
    
    override fun getRealTimeBalance(accountId: String, snapshotBalance: Long): Long {
        val delta = getDelta(accountId)
        return snapshotBalance + delta
    }
}

