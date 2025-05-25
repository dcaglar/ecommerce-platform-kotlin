package com.dogancaglar.paymentservice.adapter.redis.id

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisIdGeneratorAdapter(
    private val redis: StringRedisTemplate
) {

    fun nextId(namespace: String): Long {
        return redis.opsForValue().increment("id-generator:$namespace")
            ?: throw IllegalStateException("Redis ID generation failed for $namespace")
    }

    fun getRawValue(namespace: String): Long? {
        return redis.opsForValue().get("id-generator:$namespace")?.toLongOrNull()
    }

    fun setMinValue(namespace: String, value: Long) {
        val current = getRawValue(namespace) ?: 0
        if (value > current) {
            redis.opsForValue().set("id-generator:$namespace", value.toString())
        }
    }
}
