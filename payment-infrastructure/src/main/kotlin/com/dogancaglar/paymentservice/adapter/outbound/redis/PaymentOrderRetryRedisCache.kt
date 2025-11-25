package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.util.RetryItem
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
open class PaymentOrderRetryRedisCache(
    private val redisTemplate: StringRedisTemplate,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val queue = "payment_order_retry_queue"
    private val inflight = "payment_order_retry_inflight"   // ZSET: member = raw JSON, score = first-picked timestamp

    fun getRetryCount(paymentOrderId: Long): Int {
        val retryKey = "retry:count:$paymentOrderId"
        return redisTemplate.opsForValue()[retryKey]?.toInt() ?: 0
    }

    fun incrementAndGetRetryCount(paymentOrderId: Long): Int {
        val retryKey = "retry:count:$paymentOrderId"
        return redisTemplate.opsForValue().increment(retryKey)?.toInt() ?: 1
    }

    fun resetRetryCounter(paymentOrderId: Long) {
        val retryKey = "retry:count:$paymentOrderId"
        redisTemplate.delete(retryKey)
    }

    fun scheduleRetry(
        json: String,
        retryAt: Double
    ) {
        redisTemplate.opsForZSet().add(queue, json, retryAt)
    }

    /**
     * Polls for due retry events using a two-step process:
     *  1. Reads all entries from the Redis ZSet whose score (scheduled timestamp) is <= now.
     *  2. Removes each of those entries from the ZSet (not atomic!).
     *
     * ⚠️ Note: This is **NOT atomic**—if multiple app instances call this at the same time,
     *   the same entry could be processed by more than one instance.
     *   Fine for single-instance or dev/test, but **not safe for at-least-once delivery in cluster**.
     */
    fun pollDueRetries(): List<String> {
        val now = System.currentTimeMillis().toDouble()
        //get due items from the sorted set
        val dueItems = redisTemplate.opsForZSet().rangeByScore(queue, 0.0, now)
        //remove them from the sorted set
        dueItems?.forEach { json ->
            pureRemoveDueRetry(json)
        }
        return dueItems?.toList() ?: emptyList()
    }

    /**
     * Atomically polls (fetches AND removes) up to [max] due retry events using Redis ZPOPMIN.
     *  1. Calls ZPOPMIN to pop up to [max] items with the lowest score from the ZSet.
     *  2. Splits into:
     *     - "due" items (score <= now, i.e., ready for retry)
     *     - "not-due" items (score > now, not yet scheduled)
     *  3. If any "not-due" items were popped, **re-inserts** them back into the ZSet with their original score.
     *
     * ✅ This is **atomic** from the Redis perspective: Only your process gets these items.
     * ⚠️ Requires Redis 5.0+ for ZPOPMIN.
     * ⚠️ Because ZPOPMIN just pops the lowest-[score] items, if not all are "due" yet,
     *    you must check scores yourself and reinsert future items.
     */
    fun pollDueRetriesAtomic(max: Long = 1000): List<String> {
        val now = System.currentTimeMillis().toDouble()
        val connection = redisTemplate.connectionFactory?.connection
        val raw = connection?.zSetCommands()?.zPopMin(queue.toByteArray(), max)
        val (due, notDue) = raw?.partition { it.score <= now } ?: Pair(emptyList(), emptyList())
        // Re-insert not-due items
        notDue.forEach { connection?.zSetCommands()?.zAdd(queue.toByteArray(), it.score, it.value) }
        return due.map { String(it.value) }
    }


    fun pureRemoveDueRetry(json: String) {
        redisTemplate.opsForZSet().remove(queue, json)
    }

    fun zsetSize(): Long =
        redisTemplate.opsForZSet().zCard(queue) ?: 0L


    /**
     * Pops up to [max] due items atomically from the main ZSET and moves them
     * into an inflight ZSET. Returns the raw JSON bytes for each moved item.
     *
     * Semantics:
     *   - Items with score <= now are considered DUE and moved to inflight with score=now.
     *   - Items with score > now are reinserted back to the main queue unchanged.
     * Crash after this call will NOT lose due items; they are in 'inflight'.
     * 
     * @deprecated Use popDueToInflightDeserialized() instead, which deserializes to EventEnvelope like Kafka consumers.
     */
    @Deprecated("Use popDueToInflightDeserialized() for automatic deserialization")
    fun popDueToInflight(max: Long = 1000): List<ByteArray> {
        val now = System.currentTimeMillis().toDouble()
        return redisTemplate.execute(RedisCallback<List<ByteArray>> { conn ->
            val popped = conn.zSetCommands().zPopMin(queue.toByteArray(), max) ?: emptyList()
            if (popped.isEmpty()) return@RedisCallback emptyList()

            val due = mutableListOf<ByteArray>()
            val notDue = mutableListOf<Pair<ByteArray, Double>>()

            popped.forEach { tup ->
                val value = tup.value // ByteArray
                val score = tup.score
                if (score <= now) {
                    // move to inflight with timestamp 'now'
                    conn.zSetCommands().zAdd(inflight.toByteArray(), now, value)
                    due += value
                } else {
                    notDue += value to score
                }
            }

            // Put not-due back to main queue with original score
            notDue.forEach { (valBytes, score) ->
                conn.zSetCommands().zAdd(queue.toByteArray(), score, valBytes)
            }
            due
        }) ?: emptyList()
    }

    /**
     * Pops up to [max] due items atomically from the main ZSET and moves them
     * into an inflight ZSET. Deserializes each item to EventEnvelope (like Kafka consumers do).
     * Returns RetryItem containing both deserialized envelope and raw bytes.
     *
     * Semantics:
     *   - Items with score <= now are considered DUE and moved to inflight with score=now.
     *   - Items with score > now are reinserted back to the main queue unchanged.
     *   - Poison messages (deserialization failures) are automatically removed from inflight.
     * Crash after this call will NOT lose due items; they are in 'inflight'.
     */
    fun popDueToInflightDeserialized(max: Long = 1000): List<RetryItem> {
        val now = System.currentTimeMillis().toDouble()
        val rawItems = redisTemplate.execute(RedisCallback<List<ByteArray>> { conn ->
            val popped = conn.zSetCommands().zPopMin(queue.toByteArray(), max) ?: emptyList()
            if (popped.isEmpty()) return@RedisCallback emptyList()

            val due = mutableListOf<ByteArray>()
            val notDue = mutableListOf<Pair<ByteArray, Double>>()

            popped.forEach { tup ->
                val value = tup.value // ByteArray
                val score = tup.score
                if (score <= now) {
                    // move to inflight with timestamp 'now'
                    conn.zSetCommands().zAdd(inflight.toByteArray(), now, value)
                    due += value
                } else {
                    notDue += value to score
                }
            }

            // Put not-due back to main queue with original score
            notDue.forEach { (valBytes, score) ->
                conn.zSetCommands().zAdd(queue.toByteArray(), score, valBytes)
            }
            due
        }) ?: emptyList()

        // Deserialize each ByteArray to EventEnvelope (like Kafka deserializer does)
        val items = mutableListOf<RetryItem>()
        for (raw in rawItems) {
            try {
                val envelope: EventEnvelope<PaymentOrderCaptureCommand> =
                    objectMapper.readValue(raw, object : TypeReference<EventEnvelope<PaymentOrderCaptureCommand>>() {})
                items += RetryItem(envelope, raw)
            } catch (e: Exception) {
                // If we cannot deserialize, drop from inflight to avoid poison loops
                logger.warn("Failed to deserialize retry item, removing from inflight: ${e.message}")
                removeFromInflight(raw)
            }
        }
        return items
    }

    /** Remove one item from inflight ZSET by its exact raw JSON bytes. */
    fun removeFromInflight(raw: ByteArray) {
        redisTemplate.execute(RedisCallback<Long> { conn ->
            conn.zSetCommands().zRem(inflight.toByteArray(), raw) ?: 0L
        })
    }

    /** Number of items currently inflight. */
    fun inflightSize(): Long {
        return redisTemplate.execute(RedisCallback<Long> { conn ->
            conn.zSetCommands().zCard(inflight.toByteArray()) ?: 0L
        }) ?: 0L
    }

    /**
     * Reclaim inflight items older than [olderThanMs] back to the main queue.
     * Uses the current time as the new score (ready immediately).
     */
    fun reclaimInflight(olderThanMs: Long = 60_000) {
        val cutoff = (System.currentTimeMillis() - olderThanMs).toDouble()
        val nowScore = System.currentTimeMillis().toDouble()
        redisTemplate.execute(RedisCallback<Unit> { conn ->
            val members: MutableSet<ByteArray> =
                conn.zSetCommands().zRangeByScore(inflight.toByteArray(), 0.0, cutoff) ?: return@RedisCallback

            // Requeue each stale member as due-now, then remove from inflight
            members.forEach { member ->
                conn.zSetCommands().zAdd(queue.toByteArray(), nowScore, member)
                conn.zSetCommands().zRem(inflight.toByteArray(), member)
            }
        })
    }
}