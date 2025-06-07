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

    //we will use this to poll due retries and remove them from the queue
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

    fun pureRemoveDueRetry(json: String) {
        redisTemplate.opsForZSet().remove(queue, json)
    }
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