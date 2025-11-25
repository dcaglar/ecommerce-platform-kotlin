package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RedisEventDedupAdapter(
    private val redis: StringRedisTemplate
) : EventDeduplicationPort {

    override fun exists(eventId: String): Boolean {
        return redis.hasKey(dedupKey(eventId)) == true
    }

    override fun markProcessed(eventId: String, ttlSeconds: Long) {
        redis.opsForValue().set(
            dedupKey(eventId),
            "1",
            ttlSeconds,
            TimeUnit.SECONDS
        )
    }

    private fun dedupKey(eventId: String) = "dedup:event:$eventId"
}