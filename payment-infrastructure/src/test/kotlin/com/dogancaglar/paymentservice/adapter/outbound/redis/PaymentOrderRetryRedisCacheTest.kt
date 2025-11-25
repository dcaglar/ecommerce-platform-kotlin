package com.dogancaglar.paymentservice.adapter.outbound.redis

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisZSetCommands
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.data.redis.connection.zset.Tuple
import org.springframework.data.redis.core.RedisCallback
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.util.RetryItem
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.fasterxml.jackson.core.type.TypeReference

/**
 * Unit tests for PaymentOrderRetryRedisCache using MockK.
 * 
 * Tests verify:
 * - Retry count tracking
 * - Scheduled retry operations
 * - Inflight queue management
 * - Reclaim logic
 * - Atomic operations
 * 
 * **Serialization/Deserialization Flow:**
 * 
 * **Storage (Serialization):**
 * 1. PaymentOrderRetryQueueAdapter.scheduleRetry():
 *    - PaymentOrder → EventEnvelope<PaymentOrderCaptureCommand>
 *    - EventEnvelope → JSON String: objectMapper.writeValueAsString(envelope)
 *    - JSON String → Redis ZSet: paymentOrderRetryRedisCache.scheduleRetry(json, retryAt)
 *    - Redis stores JSON string (internally as UTF-8 ByteArray)
 * 
 * **Retrieval & Deserialization (Cache Layer - Like Kafka):**
 * 2. PaymentOrderRetryRedisCache.popDueToInflightDeserialized():
 *    - Redis ZSet → List<RetryItem> (deserialized EventEnvelope + raw bytes)
 *    - Uses low-level Redis connection (zSetCommands().zPopMin())
 *    - Deserializes each ByteArray → EventEnvelope (like Kafka EventEnvelopeKafkaDeserializer)
 *    - Returns List<RetryItem> containing:
 *      - Deserialized EventEnvelope<PaymentOrderCaptureCommand> (for business logic)
 *      - Raw ByteArray (for removeFromInflight() which needs exact byte match)
 *    - Handles poison messages by removing them from inflight
 * 
 * **Adapter Layer (Simplified):**
 * 3. PaymentOrderRetryQueueAdapter.pollDueRetriesToInflight():
 *    - Simply calls cache.popDueToInflightDeserialized()
 *    - No deserialization needed - already done in cache layer (like Kafka consumers)
 * 
 * **Removal:**
 * 4. removeFromInflight(): Uses raw ByteArray to remove from inflight set
 *    - Must match exact bytes stored in Redis (no re-serialization)
 *    - This is why RetryItem keeps both envelope AND raw bytes
 */
class PaymentOrderRetryRedisCacheTest {
    
    // Helper function to create a mocked Tuple
    private fun createMockedTuple(value: ByteArray, score: Double): Tuple {
        return mockk {
            every { getValue() } returns value
            every { getScore() } returns score
        }
    }

    // Helper to create a valid EventEnvelope for testing
    private fun createTestEventEnvelope(paymentOrderId: String = "123", traceId: String = "trace-123", retryCount: Int = 1): EventEnvelope<PaymentOrderCaptureCommand> {
        val now = Utc.nowInstant()
        // When retryCount > 0, status must be PENDING_CAPTURE (retryable status)
        // When retryCount = 0, status must be CAPTURE_REQUESTED
        val status = if (retryCount > 0) PaymentOrderStatus.PENDING_CAPTURE else PaymentOrderStatus.CAPTURE_REQUESTED
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = PaymentOrderId(paymentOrderId.toLong()),
            paymentId = PaymentId(999L),
            sellerId = SellerId("111"),
            amount = Amount.of(10000L, Currency("USD")),
            status = status,
            retryCount = retryCount,
            createdAt = Utc.fromInstant(now),
            updatedAt = Utc.fromInstant(now)
        )
        val captureCommand = PaymentOrderCaptureCommand.from(paymentOrder, now, attempt = 1)
        return EventEnvelopeFactory.envelopeFor(
            data = captureCommand,
            aggregateId = captureCommand.paymentOrderId,
            traceId = traceId,
            parentEventId = null
        )
    }

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOperations: ValueOperations<String, String>
    private lateinit var zSetOperations: ZSetOperations<String, String>
    private lateinit var connectionFactory: RedisConnectionFactory
    private lateinit var connection: RedisConnection
    private lateinit var zSetCommands: RedisZSetCommands
    private lateinit var objectMapper: ObjectMapper
    private lateinit var cache: PaymentOrderRetryRedisCache

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk(relaxed = true)
        valueOperations = mockk(relaxed = true)
        zSetOperations = mockk(relaxed = true)
        connectionFactory = mockk(relaxed = true)
        connection = mockk(relaxed = true)
        zSetCommands = mockk(relaxed = true)
        
        // Create ObjectMapper for deserialization (like in production)
        objectMapper = ObjectMapper()
            .registerModule(JavaTimeModule())
            .registerKotlinModule()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        every { redisTemplate.opsForValue() } returns valueOperations
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { redisTemplate.connectionFactory } returns connectionFactory
        every { connectionFactory.connection } returns connection
        every { connection.zSetCommands() } returns zSetCommands

        cache = PaymentOrderRetryRedisCache(redisTemplate, objectMapper)
    }

    // ==================== Retry Counter Tests ====================

    @Test
    fun `getRetryCount should return 0 when counter does not exist`() {
        // Given
        val paymentOrderId = 123L
        val retryKey = "retry:count:123"
        every { valueOperations.get(retryKey) } returns null

        // When
        val count = cache.getRetryCount(paymentOrderId)

        // Then
        assertEquals(0, count)
        verify(exactly = 1) { valueOperations.get(retryKey) }
    }

    @Test
    fun `getRetryCount should return existing count`() {
        // Given
        val paymentOrderId = 456L
        val retryKey = "retry:count:456"
        every { valueOperations.get(retryKey) } returns "3"

        // When
        val count = cache.getRetryCount(paymentOrderId)

        // Then
        assertEquals(3, count)
        verify(exactly = 1) { valueOperations.get(retryKey) }
    }

    @Test
    fun `incrementAndGetRetryCount should increment counter`() {
        // Given
        val paymentOrderId = 789L
        val retryKey = "retry:count:789"
        every { valueOperations.increment(retryKey) } returns 1L

        // When
        val count = cache.incrementAndGetRetryCount(paymentOrderId)

        // Then
        assertEquals(1, count)
        verify(exactly = 1) { valueOperations.increment(retryKey) }
    }

    @Test
    fun `incrementAndGetRetryCount should return 1 when increment returns null`() {
        // Given
        val paymentOrderId = 999L
        val retryKey = "retry:count:999"
        every { valueOperations.increment(retryKey) } returns null

        // When
        val count = cache.incrementAndGetRetryCount(paymentOrderId)

        // Then
        assertEquals(1, count)
        verify(exactly = 1) { valueOperations.increment(retryKey) }
    }

    @Test
    fun `resetRetryCounter should delete counter key`() {
        // Given
        val paymentOrderId = 111L
        val retryKey = "retry:count:111"
        every { redisTemplate.delete(retryKey) } returns true

        // When
        cache.resetRetryCounter(paymentOrderId)

        // Then
        verify(exactly = 1) { redisTemplate.delete(retryKey) }
    }

    // ==================== Schedule Retry Tests ====================

    @Test
    fun `scheduleRetry should add to sorted set with correct score`() {
        // Given
        val json = """{"eventId":"evt-123"}"""
        val retryAt = 1234567890.0
        every { zSetOperations.add("payment_order_retry_queue", json, retryAt) } returns true

        // When
        cache.scheduleRetry(json, retryAt)

        // Then
        verify(exactly = 1) { zSetOperations.add("payment_order_retry_queue", json, retryAt) }
    }

    @Test
    fun `pureRemoveDueRetry should remove from sorted set`() {
        // Given
        val json = """{"eventId":"evt-456"}"""
        every { zSetOperations.remove("payment_order_retry_queue", json) } returns 1L

        // When
        cache.pureRemoveDueRetry(json)

        // Then
        verify(exactly = 1) { zSetOperations.remove("payment_order_retry_queue", json) }
    }

    @Test
    fun `zsetSize should return queue size`() {
        // Given
        every { zSetOperations.zCard("payment_order_retry_queue") } returns 42L

        // When
        val size = cache.zsetSize()

        // Then
        assertEquals(42L, size)
        verify(exactly = 1) { zSetOperations.zCard("payment_order_retry_queue") }
    }

    @Test
    fun `zsetSize should return 0 when zCard returns null`() {
        // Given
        every { zSetOperations.zCard("payment_order_retry_queue") } returns null

        // When
        val size = cache.zsetSize()

        // Then
        assertEquals(0L, size)
    }

    // ==================== Inflight Tests ====================

    @Test
    fun `popDueToInflightDeserialized should return empty list when no items to pop`() {
        // Given
        val queueBytes = "payment_order_retry_queue".toByteArray()
        every { zSetCommands.zPopMin(queueBytes, 1000) } returns emptySet()
        every { redisTemplate.execute<List<ByteArray>>(any<RedisCallback<List<ByteArray>>>()) } answers {
            val callback = firstArg<RedisCallback<List<ByteArray>>>()
            callback.doInRedis(connection)
        }

        // When
        val result = cache.popDueToInflightDeserialized(1000)

        // Then
        assertTrue(result.isEmpty())
        verify(exactly = 1) { zSetCommands.zPopMin(queueBytes, 1000) }
    }

    @Test
    fun `popDueToInflightDeserialized should move due items to inflight and deserialize`() {
        // Given
        val now = System.currentTimeMillis().toDouble()
        val dueScore = now - 1000 // Past
        // Create a valid EventEnvelope JSON (as it would be stored in Redis)
        val envelope = createTestEventEnvelope("123", "trace-123")
        val jsonBytes = objectMapper.writeValueAsBytes(envelope)
        
        val tuple = createMockedTuple(jsonBytes, dueScore)
        
        every { zSetCommands.zPopMin("payment_order_retry_queue".toByteArray(), 1000) } returns setOf(tuple)
        every { zSetCommands.zAdd("payment_order_retry_inflight".toByteArray(), any<Double>(), jsonBytes) } returns true
        every { redisTemplate.execute<List<ByteArray>>(any<RedisCallback<List<ByteArray>>>()) } answers {
            val callback = firstArg<RedisCallback<List<ByteArray>>>()
            callback.doInRedis(connection)
        }

        // When
        val result = cache.popDueToInflightDeserialized(1000)

        // Then
        assertEquals(1, result.size)
        val retryItem = result[0]
        // Verify deserialized envelope
        assertEquals(envelope.eventId, retryItem.envelope.eventId)
        assertEquals("123", retryItem.envelope.data.paymentOrderId)
        // Verify raw bytes are preserved
        assertArrayEquals(jsonBytes, retryItem.raw)
        verify(exactly = 1) { 
            zSetCommands.zAdd("payment_order_retry_inflight".toByteArray(), any(), jsonBytes) 
        }
    }

    @Test
    fun `popDueToInflightDeserialized should requeue not-due items`() {
        // Given
        val now = System.currentTimeMillis().toDouble()
        val futureScore = now + 10000 // Future
        // Create a valid EventEnvelope JSON
        val envelope = createTestEventEnvelope("123", "trace-123")
        val jsonBytes = objectMapper.writeValueAsBytes(envelope)
        
        val tuple = createMockedTuple(jsonBytes, futureScore)
        
        every { zSetCommands.zPopMin("payment_order_retry_queue".toByteArray(), 1000) } returns setOf(tuple)
        every { zSetCommands.zAdd("payment_order_retry_queue".toByteArray(), futureScore, jsonBytes) } returns true
        every { redisTemplate.execute<List<ByteArray>>(any<RedisCallback<List<ByteArray>>>()) } answers {
            val callback = firstArg<RedisCallback<List<ByteArray>>>()
            callback.doInRedis(connection)
        }

        // When
        val result = cache.popDueToInflightDeserialized(1000)

        // Then
        assertTrue(result.isEmpty(), "Not-due items should not be returned")
        // Verify the JSON bytes are preserved when requeued
        verify(exactly = 1) { 
            zSetCommands.zAdd("payment_order_retry_queue".toByteArray(), futureScore, jsonBytes) 
        }
        verify(exactly = 0) { 
            zSetCommands.zAdd("payment_order_retry_inflight".toByteArray(), any<Double>(), any<ByteArray>()) 
        }
    }

    @Test
    fun `popDueToInflightDeserialized should handle mixed due and not-due items`() {
        // Given
        val now = System.currentTimeMillis().toDouble()
        
        // Create valid EventEnvelope JSONs
        val dueEnvelope = createTestEventEnvelope("123", "trace-due")
        val dueBytes = objectMapper.writeValueAsBytes(dueEnvelope)
        val dueTuple = createMockedTuple(dueBytes, now - 1000)
        
        val futureEnvelope = createTestEventEnvelope("789", "trace-future")
        val futureBytes = objectMapper.writeValueAsBytes(futureEnvelope)
        val futureTuple = createMockedTuple(futureBytes, now + 10000)
        
        every { zSetCommands.zPopMin("payment_order_retry_queue".toByteArray(), 1000) } returns setOf(dueTuple, futureTuple)
        every { zSetCommands.zAdd(any(), any<Double>(), any()) } returns true
        every { redisTemplate.execute<List<ByteArray>>(any<RedisCallback<List<ByteArray>>>()) } answers {
            val callback = firstArg<RedisCallback<List<ByteArray>>>()
            callback.doInRedis(connection)
        }

        // When
        val result = cache.popDueToInflightDeserialized(1000)

        // Then
        assertEquals(1, result.size)
        val retryItem = result[0]
        // Verify deserialized envelope
        assertEquals(dueEnvelope.eventId, retryItem.envelope.eventId)
        assertEquals("123", retryItem.envelope.data.paymentOrderId)
        // Verify raw bytes
        assertArrayEquals(dueBytes, retryItem.raw)
        
        // Verify due item moved to inflight
        verify(exactly = 1) { 
            zSetCommands.zAdd("payment_order_retry_inflight".toByteArray(), any<Double>(), dueBytes) 
        }
        
        // Verify future item requeued
        verify(exactly = 1) { 
            zSetCommands.zAdd("payment_order_retry_queue".toByteArray(), now + 10000, futureBytes) 
        }
    }

    @Test
    fun `removeFromInflight should remove item from inflight set`() {
        // Given
        val raw = """{"eventId":"evt-123"}""".toByteArray()
        every { zSetCommands.zRem("payment_order_retry_inflight".toByteArray(), raw) } returns 1L
        every { redisTemplate.execute<Long>(any<RedisCallback<Long>>()) } answers {
            val callback = firstArg<RedisCallback<Long>>()
            callback.doInRedis(connection)
        }

        // When
        cache.removeFromInflight(raw)

        // Then
        verify(exactly = 1) { zSetCommands.zRem("payment_order_retry_inflight".toByteArray(), raw) }
    }

    @Test
    fun `inflightSize should return number of inflight items`() {
        // Given
        every { zSetCommands.zCard("payment_order_retry_inflight".toByteArray()) } returns 5L
        every { redisTemplate.execute<Long>(any<RedisCallback<Long>>()) } answers {
            val callback = firstArg<RedisCallback<Long>>()
            callback.doInRedis(connection)
        }

        // When
        val size = cache.inflightSize()

        // Then
        assertEquals(5L, size)
        verify(exactly = 1) { zSetCommands.zCard("payment_order_retry_inflight".toByteArray()) }
    }

    @Test
    fun `inflightSize should return 0 when zCard returns null`() {
        // Given
        every { zSetCommands.zCard("payment_order_retry_inflight".toByteArray()) } returns null
        every { redisTemplate.execute<Long>(any<RedisCallback<Long>>()) } answers {
            val callback = firstArg<RedisCallback<Long>>()
            callback.doInRedis(connection)
        }

        // When
        val size = cache.inflightSize()

        // Then
        assertEquals(0L, size)
    }

    // ==================== Reclaim Tests ====================

    @Test
    fun `reclaimInflight should move stale items back to queue`() {
        // Given
        val olderThanMs = 60_000L
        val staleBytes = """{"eventId":"stale"}""".toByteArray()
        
        every { 
            zSetCommands.zRangeByScore("payment_order_retry_inflight".toByteArray(), 0.0, any()) 
        } returns mutableSetOf(staleBytes)
        
        every { zSetCommands.zAdd(any(), any<Double>(), any()) } returns true
        every { zSetCommands.zRem(any(), any()) } returns 1L
        every { redisTemplate.execute<Unit>(any<RedisCallback<Unit>>()) } answers {
            val callback = firstArg<RedisCallback<Unit>>()
            callback.doInRedis(connection)
        }

        // When
        cache.reclaimInflight(olderThanMs)

        // Then - verify the actual calls made through the connection
        verify(exactly = 1) { 
            zSetCommands.zRangeByScore("payment_order_retry_inflight".toByteArray(), 0.0, any()) 
        }
        verify(exactly = 1) { 
            zSetCommands.zAdd("payment_order_retry_queue".toByteArray(), any<Double>(), staleBytes) 
        }
        verify(exactly = 1) { 
            zSetCommands.zRem("payment_order_retry_inflight".toByteArray(), staleBytes) 
        }
    }

    @Test
    fun `reclaimInflight should handle empty inflight set`() {
        // Given
        val olderThanMs = 60_000L
        val cutoff = (System.currentTimeMillis() - olderThanMs).toDouble()
        
        every { 
            zSetCommands.zRangeByScore("payment_order_retry_inflight".toByteArray(), 0.0, cutoff) 
        } returns null
        every { redisTemplate.execute<Unit>(any<RedisCallback<Unit>>()) } answers {
            val callback = firstArg<RedisCallback<Unit>>()
            callback.doInRedis(connection)
        }

        // When
        cache.reclaimInflight(olderThanMs)

        // Then
        verify(exactly = 0) { zSetCommands.zAdd(any<ByteArray>(), any<Double>(), any<ByteArray>()) }
        verify(exactly = 0) { zSetCommands.zRem(any(), any()) }
    }

    @Test
    fun `reclaimInflight should handle multiple stale items`() {
        // Given
        val olderThanMs = 60_000L
        val stale1 = """{"eventId":"stale1"}""".toByteArray()
        val stale2 = """{"eventId":"stale2"}""".toByteArray()
        val stale3 = """{"eventId":"stale3"}""".toByteArray()
        
        every { 
            zSetCommands.zRangeByScore("payment_order_retry_inflight".toByteArray(), 0.0, any()) 
        } returns mutableSetOf(stale1, stale2, stale3)
        
        every { zSetCommands.zAdd(any(), any<Double>(), any()) } returns true
        every { zSetCommands.zRem(any(), any()) } returns 1L
        every { redisTemplate.execute<Unit>(any<RedisCallback<Unit>>()) } answers {
            val callback = firstArg<RedisCallback<Unit>>()
            callback.doInRedis(connection)
        }

        // When
        cache.reclaimInflight(olderThanMs)

        // Then - verify the actual calls made through the connection
        verify(exactly = 1) { 
            zSetCommands.zRangeByScore("payment_order_retry_inflight".toByteArray(), 0.0, any()) 
        }
        verify(exactly = 3) { 
            zSetCommands.zAdd("payment_order_retry_queue".toByteArray(), any<Double>(), any<ByteArray>()) 
        }
        verify(exactly = 3) { 
            zSetCommands.zRem("payment_order_retry_inflight".toByteArray(), any()) 
        }
    }

    @Test
    fun `reclaimInflight should use custom olderThanMs parameter`() {
        // Given
        val customOlderThanMs = 120_000L
        
        every { 
            zSetCommands.zRangeByScore("payment_order_retry_inflight".toByteArray(), 0.0, any()) 
        } returns mutableSetOf()
        every { redisTemplate.execute<Unit>(any<RedisCallback<Unit>>()) } answers {
            val callback = firstArg<RedisCallback<Unit>>()
            callback.doInRedis(connection)
        }

        // When
        cache.reclaimInflight(customOlderThanMs)

        // Then - verify cutoff calculation
        verify(exactly = 1) { 
            zSetCommands.zRangeByScore("payment_order_retry_inflight".toByteArray(), 0.0, any()) 
        }
    }
}

