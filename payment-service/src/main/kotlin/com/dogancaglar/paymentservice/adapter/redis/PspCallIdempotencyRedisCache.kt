package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

@Repository
class PspCallIdempotencyRedisCache(
    private val redisTemplate: StringRedisTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val keyPrefix = "psp:call:result:"
    private val eventKeyPrefix = "psp:call:event:"

    fun getPspResult(paymentOrderId: String): PaymentOrderStatus? {
        val key = keyPrefix + paymentOrderId
        val value = redisTemplate.opsForValue().get(key)
        return value?.let { runCatching { PaymentOrderStatus.valueOf(it) }.getOrNull() }
    }

    fun setPspResult(paymentOrderId: String, status: PaymentOrderStatus, ttlMinutes: Long = 30) {
        val key = keyPrefix + paymentOrderId
        redisTemplate.opsForValue().set(key, status.name, ttlMinutes, TimeUnit.MINUTES)
    }

    fun isEventProcessed(eventId: String): Boolean {
        val key = eventKeyPrefix + eventId
        return redisTemplate.hasKey(key) == true
    }

    fun markEventProcessed(eventId: String, ttlMinutes: Long = 30) {
        val key = eventKeyPrefix + eventId
        redisTemplate.opsForValue().set(key, "1", ttlMinutes, TimeUnit.MINUTES)
    }
}
