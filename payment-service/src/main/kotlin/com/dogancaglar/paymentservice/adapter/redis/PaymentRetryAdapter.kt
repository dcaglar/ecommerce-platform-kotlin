package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component


@Component("paymentRetryAdapter")
open class PaymentRetryAdapter(private val redisTemplate: StringRedisTemplate) : RetryQueuePort {
    private val queue = "payment_retry_queue"
    override fun scheduleRetry(paymentOrderId: String, retryCount: Int) {
        val delayMillis = calculateBackoffMillis(retryCount)
        val retryAt = System.currentTimeMillis() + delayMillis
        redisTemplate.opsForZSet().add(queue, paymentOrderId, retryAt.toDouble())
    }

    override fun pollDueRetries(): List<String> {
        val  now = System.currentTimeMillis().toDouble()
        val dueItems = redisTemplate.opsForZSet().rangeByScore(queue, 0.0, now)
        dueItems?.forEach { redisTemplate.opsForZSet().remove(queue, it) }
        return dueItems?.toList() ?: emptyList()
    }


    fun calculateBackoffMillis(retryCount: Int): Long {
        val baseDelay = 5_000L // 5 seconds
        return baseDelay * (retryCount + 1) // Linear or exponential backoff
    }
}