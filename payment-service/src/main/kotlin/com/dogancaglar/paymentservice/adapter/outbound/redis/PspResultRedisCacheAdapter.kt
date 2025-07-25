package com.dogancaglar.infrastructure.redis

import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.port.outbound.PspResultCachePort
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
        redisTemplate.delete(prefix + pspKey.value)
    }
}
