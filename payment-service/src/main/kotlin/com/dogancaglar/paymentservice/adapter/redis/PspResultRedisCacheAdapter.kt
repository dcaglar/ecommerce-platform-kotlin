package com.dogancaglar.paymentservice.adapter.redis

import com.dogancaglar.paymentservice.domain.port.PspResultCachePort
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

    override fun put(pspKey: String, resultJson: String) {
        redisTemplate.opsForValue().set(prefix + pspKey, resultJson, ttlSeconds, TimeUnit.SECONDS)
    }

    override fun get(pspKey: String): String? {
        return redisTemplate.opsForValue().get(prefix + pspKey)
    }

    override fun remove(pspKey: String) {
        redisTemplate.delete(prefix + pspKey)
    }
}
