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

/**
 * Unit tests for PaymentOrderRetryRedisCache using MockK.
 * 
 * Tests verify:
 * - Retry count tracking
 * - Scheduled retry operations
 * - Inflight queue management
 * - Reclaim logic
 * - Atomic operations
 */
class PaymentOrderRetryRedisCacheTest {
    
    // Helper function to create a mocked Tuple
    private fun createMockedTuple(value: ByteArray, score: Double): Tuple {
        return mockk {
            every { getValue() } returns value
            every { getScore() } returns score
        }
    }

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOperations: ValueOperations<String, String>
    private lateinit var zSetOperations: ZSetOperations<String, String>
    private lateinit var connectionFactory: RedisConnectionFactory
    private lateinit var connection: RedisConnection
    private lateinit var zSetCommands: RedisZSetCommands
    private lateinit var cache: PaymentOrderRetryRedisCache

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk(relaxed = true)
        valueOperations = mockk(relaxed = true)
        zSetOperations = mockk(relaxed = true)
        connectionFactory = mockk(relaxed = true)
        connection = mockk(relaxed = true)
        zSetCommands = mockk(relaxed = true)

        every { redisTemplate.opsForValue() } returns valueOperations
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { redisTemplate.connectionFactory } returns connectionFactory
        every { connectionFactory.connection } returns connection
        every { connection.zSetCommands() } returns zSetCommands

        cache = PaymentOrderRetryRedisCache(redisTemplate)
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
    fun `popDueToInflight should return empty list when no items to pop`() {
        // Given
        val queueBytes = "payment_order_retry_queue".toByteArray()
        every { zSetCommands.zPopMin(queueBytes, 1000) } returns emptySet()

        // When
        val result = cache.popDueToInflight(1000)

        // Then
        assertTrue(result.isEmpty())
        verify(exactly = 1) { zSetCommands.zPopMin(queueBytes, 1000) }
    }

    @Test
    fun `popDueToInflight should move due items to inflight`() {
        // Given
        val now = System.currentTimeMillis().toDouble()
        val dueScore = now - 1000 // Past
        val jsonBytes = """{"eventId":"evt-due"}""".toByteArray()
        
        val tuple = createMockedTuple(jsonBytes, dueScore)
        
        every { zSetCommands.zPopMin("payment_order_retry_queue".toByteArray(), 1000) } returns setOf(tuple)
        every { zSetCommands.zAdd("payment_order_retry_inflight".toByteArray(), any<Double>(), jsonBytes) } returns true

        // When
        val result = cache.popDueToInflight(1000)

        // Then
        assertEquals(1, result.size)
        assertArrayEquals(jsonBytes, result[0])
        verify(exactly = 1) { 
            zSetCommands.zAdd("payment_order_retry_inflight".toByteArray(), any(), jsonBytes) 
        }
    }

    @Test
    fun `popDueToInflight should requeue not-due items`() {
        // Given
        val now = System.currentTimeMillis().toDouble()
        val futureScore = now + 10000 // Future
        val jsonBytes = """{"eventId":"evt-future"}""".toByteArray()
        
        val tuple = createMockedTuple(jsonBytes, futureScore)
        
        every { zSetCommands.zPopMin("payment_order_retry_queue".toByteArray(), 1000) } returns setOf(tuple)
        every { zSetCommands.zAdd("payment_order_retry_queue".toByteArray(), futureScore, jsonBytes) } returns true

        // When
        val result = cache.popDueToInflight(1000)

        // Then
        assertTrue(result.isEmpty(), "Not-due items should not be returned")
        verify(exactly = 1) { 
            zSetCommands.zAdd("payment_order_retry_queue".toByteArray(), futureScore, jsonBytes) 
        }
        verify(exactly = 0) { 
            zSetCommands.zAdd("payment_order_retry_inflight".toByteArray(), any<Double>(), any<ByteArray>()) 
        }
    }

    @Test
    fun `popDueToInflight should handle mixed due and not-due items`() {
        // Given
        val now = System.currentTimeMillis().toDouble()
        
        val dueBytes = """{"eventId":"due"}""".toByteArray()
        val dueTuple = createMockedTuple(dueBytes, now - 1000)
        
        val futureBytes = """{"eventId":"future"}""".toByteArray()
        val futureTuple = createMockedTuple(futureBytes, now + 10000)
        
        every { zSetCommands.zPopMin("payment_order_retry_queue".toByteArray(), 1000) } returns setOf(dueTuple, futureTuple)
        every { zSetCommands.zAdd(any(), any<Double>(), any()) } returns true

        // When
        val result = cache.popDueToInflight(1000)

        // Then
        assertEquals(1, result.size)
        assertArrayEquals(dueBytes, result[0])
        
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

        // When
        cache.removeFromInflight(raw)

        // Then
        verify(exactly = 1) { zSetCommands.zRem("payment_order_retry_inflight".toByteArray(), raw) }
    }

    @Test
    fun `inflightSize should return number of inflight items`() {
        // Given
        every { zSetCommands.zCard("payment_order_retry_inflight".toByteArray()) } returns 5L

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

        // When
        cache.reclaimInflight(customOlderThanMs)

        // Then - verify cutoff calculation
        verify(exactly = 1) { 
            zSetCommands.zRangeByScore("payment_order_retry_inflight".toByteArray(), 0.0, any()) 
        }
    }
}

