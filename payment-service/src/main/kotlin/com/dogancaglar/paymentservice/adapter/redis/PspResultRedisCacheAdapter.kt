package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.payment.application.port.outbound.PspResultCachePort
import com.dogancaglar.payment.domain.model.vo.PaymentOrderId
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class PspResultRedisCacheAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${psp.cache.ttl-seconds:3600}")
    private val ttlSeconds: Long
) : PspResultCachePort {
    private val prefix = "psp_result:"

    override fun put(pspKey: PaymentOrderId, resultJson: String) {
        redisTemplate.opsForValue().set(prefix + pspKey.value, resultJson, ttlSeconds, TimeUnit.SECONDS)
    }

    override fun get(pspKey: PaymentOrderId): String? {
        return redisTemplate.opsForValue().get(prefix + pspKey.value)
    }

    override fun remove(pspKey: PaymentOrderId) {
        redisTemplate.delete(prefix + pspKey)
    }
}
