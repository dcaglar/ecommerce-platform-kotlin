package com.dogancaglar.paymentservice.adapter.redis.id

import com.dogancaglar.paymentservice.domain.port.IdGeneratorPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisIdGenerator(
    private val redis: StringRedisTemplate
) : IdGeneratorPort {

    override fun nextId(namespace: String): Long {
        return redis.opsForValue().increment("id-generator:$namespace")
            ?: throw IllegalStateException("Redis ID generation failed for $namespace")
    }

    override fun getRawValue(namespace: String): Long? {
        return redis.opsForValue().get("id-generator:$namespace")?.toLongOrNull()
    }

    override fun setMinValue(namespace: String, value: Long) {
        val current = getRawValue(namespace) ?: 0
        if (value > current) {
            redis.opsForValue().set("id-generator:$namespace", value.toString())
        }
    }
}

}