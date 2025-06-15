package com.dogancaglar.paymentservice.adapter.redis

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository

@Repository
open class PaymentRetryRedisCache(
    private val redisTemplate: StringRedisTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val queue = "payment_retry_queue"
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
    /*
    fun scheduleRetry(
        paymentOrder: PaymentOrder,
        backOffMillis: Long
    ) {
        // ... envelope creation, scheduling logic unchanged ...
        val paymentOrderRetryRequested = PaymentOrderEventMapper.toPaymentOrderRetryRequestEvent(order = paymentOrder)
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = paymentOrderRetryRequested,
            eventMetaData = EventMetadatas.PaymentOrderRetryRequestedMetadata,
            aggregateId = paymentOrderRetryRequested.publicPaymentOrderId,
            traceId = LogContext.getTraceId()!!,
            parentEventId = LogContext.getEventId()
        )
        LogContext.with(
            envelope = envelope, additionalContext = mapOf<String, String>(
                LogFields.RETRY_COUNT to paymentOrderRetryRequested.retryCount.toString(),
                LogFields.RETRY_BACKOFF_MILLIS to backOffMillis.toString(),
                LogFields.RETRY_REASON to "PSP_TIMEOUT",
                LogFields.RETRY_ERROR_MESSAGE to "PSP call timed out, retrying"
            )
        ) {
            {
                logger.info("Sending to redis with expantoal backoff jittery $")
                val json = objectMapper.writeValueAsString(envelope);
                val retryAt = System.currentTimeMillis() + backOffMillis
                redisTemplate.opsForZSet().add(queue, json, retryAt.toDouble())
            }
        }
    }


    override fun pollDueRetries(): List<EventEnvelope<PaymentOrderRetryRequested>> {
        val now = System.currentTimeMillis().toDouble()
        val dueItems = redisTemplate.opsForZSet().rangeByScore(queue, 0.0, now)

        val dueEnvelops = dueItems?.mapNotNull { json ->
            try {
                // Deserializing the full EventEnvelope
                val envelope: EventEnvelope<PaymentOrderRetryRequested> =
                    objectMapper.readValue(json, object : TypeReference<EventEnvelope<PaymentOrderRetryRequested>>() {})
                redisTemplate.opsForZSet().remove(queue, json)
                envelope // You return only the domain event
            } catch (e: Exception) {
                // Optionally log and skip corrupted entries
                null
            }
        } ?: emptyList()
        return dueEnvelops
    }

    */


    // ...pollDueRetries, resetRetryCounter as before...
}