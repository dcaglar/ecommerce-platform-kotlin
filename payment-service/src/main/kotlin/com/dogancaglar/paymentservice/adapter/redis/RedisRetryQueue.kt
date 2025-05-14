package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import org.springframework.data.redis.core.StringRedisTemplate

abstract class RedisRetryQueue(
    protected val redisTemplate: StringRedisTemplate,
    protected val queueKey: String
) : RetryQueuePort {
    override fun scheduleRetry(paymentOrderId: String, delayMillis: Long) {
        val retryAt = System.currentTimeMillis() + delayMillis
        redisTemplate.opsForZSet().add(queueKey, paymentOrderId, retryAt.toDouble())
    }

    override fun pollDueRetries(): List<String> {
        val now = System.currentTimeMillis().toDouble()
        val dueItems = redisTemplate.opsForZSet().rangeByScore(queueKey, 0.0, now)
        dueItems?.forEach { redisTemplate.opsForZSet().remove(queueKey, it) }
        return dueItems?.toList() ?: emptyList()
    }
}