package com.dogancaglar.paymentservice.adapter.redis.id

import com.dogancaglar.paymentservice.domain.port.IdGenerator
import de.huxhorn.sulky.ulid.ULID
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisIdGenerator(
    private val redisTemplate: StringRedisTemplate
) : IdGenerator {


    private val ulid = ULID()

    override fun nextId(namespace: String): Long {
        val key = redisKey(namespace)
        return redisTemplate.opsForValue().increment(key)
            ?: throw IllegalStateException("Redis INCR failed for $key")
    }

    override fun nextPublicId(prefix: String): String {
        return "$prefix-${ulid.nextULID()}"
    }

    override fun getRawValue(namespace: String): Long? {
        return redisTemplate.opsForValue().get(redisKey(namespace))?.toLongOrNull()
    }

    override fun setMinValue(namespace: String, value: Long) {
        val key = redisKey(namespace)
        val current = getRawValue(namespace) ?: 0L
        if (value > current) {
            redisTemplate.opsForValue().set(key, value.toString())
        }
    }

    private fun redisKey(namespace: String): String {
        return "id-generator:$namespace"
    }
}
