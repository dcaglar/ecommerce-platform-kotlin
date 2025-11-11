package com.dogancaglar.paymentservice.adapter.outbound.redis

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Integration tests for PaymentOrderRetryRedisCache with real Redis (Testcontainers).
 * 
 * These tests validate:
 * - Real Redis Sorted Set operations
 * - Atomic ZPOPMIN behavior
 * - Inflight queue management
 * - Concurrent access patterns
 * - Reclaim logic with real timing
 * 
 * Tagged as @integration for selective execution.
 */
@Tag("integration")
@SpringBootTest(classes = [PaymentOrderRetryRedisCacheIntegrationTest.TestConfig::class])
@Testcontainers
class PaymentOrderRetryRedisCacheIntegrationTest {

    @Configuration
    @Import(RedisAutoConfiguration::class, PaymentOrderRetryRedisCache::class)
    class TestConfig

    companion object {
        @Container
        @JvmStatic
        val redisContainer = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(false)

        @DynamicPropertySource
        @JvmStatic
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.firstMappedPort }
        }
    }

    @Autowired
    private lateinit var cache: PaymentOrderRetryRedisCache

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun setUp() {
        redisTemplate.connectionFactory?.connection?.flushAll()
    }

    // ==================== Retry Counter Tests ====================

    @Test
    fun `retry counter should increment correctly with real Redis`() {
        // Given
        val paymentOrderId = 123L

        // When
        val count1 = cache.incrementAndGetRetryCount(paymentOrderId)
        val count2 = cache.incrementAndGetRetryCount(paymentOrderId)
        val count3 = cache.incrementAndGetRetryCount(paymentOrderId)

        // Then
        assertEquals(1, count1)
        assertEquals(2, count2)
        assertEquals(3, count3)
        assertEquals(3, cache.getRetryCount(paymentOrderId))
    }

    @Test
    fun `resetRetryCounter should delete counter in Redis`() {
        // Given
        val paymentOrderId = 456L
        cache.incrementAndGetRetryCount(paymentOrderId)
        cache.incrementAndGetRetryCount(paymentOrderId)
        assertEquals(2, cache.getRetryCount(paymentOrderId))

        // When
        cache.resetRetryCounter(paymentOrderId)

        // Then
        assertEquals(0, cache.getRetryCount(paymentOrderId))
    }

    // ==================== Schedule and Poll Tests ====================

    @Test
    fun `scheduleRetry and pollDueRetries should work with real Redis`() {
        // Given
        val json = """{"eventId":"evt-123"}"""
        val now = System.currentTimeMillis()
        val pastTime = (now - 1000).toDouble()

        // When
        cache.scheduleRetry(json, pastTime)
        val polled = cache.pollDueRetries()

        // Then
        assertEquals(1, polled.size)
        assertEquals(json, polled[0])
        assertEquals(0L, cache.zsetSize())
    }

    @Test
    fun `pollDueRetries should not return future items`() {
        // Given
        val json = """{"eventId":"evt-future"}"""
        val futureTime = (System.currentTimeMillis() + 10000).toDouble()

        // When
        cache.scheduleRetry(json, futureTime)
        val polled = cache.pollDueRetries()

        // Then
        assertTrue(polled.isEmpty())
        assertEquals(1L, cache.zsetSize())
    }

    @Test
    fun `popDueToInflight should atomically move due items with real Redis`() {
        // Given
        val json1 = """{"eventId":"evt-1"}"""
        val json2 = """{"eventId":"evt-2"}"""
        val now = System.currentTimeMillis()
        val pastTime = (now - 1000).toDouble()

        cache.scheduleRetry(json1, pastTime)
        cache.scheduleRetry(json2, pastTime)

        // When
        val popped = cache.popDueToInflight(10)

        // Then
        assertEquals(2, popped.size)
        assertEquals(0L, cache.zsetSize(), "Main queue should be empty")
        assertEquals(2L, cache.inflightSize(), "Inflight should have 2 items")
    }

    @Test
    fun `popDueToInflight should requeue not-due items`() {
        // Given
        val dueJson = """{"eventId":"due"}"""
        val futureJson = """{"eventId":"future"}"""
        val now = System.currentTimeMillis()
        val pastTime = (now - 1000).toDouble()
        val futureTime = (now + 10000).toDouble()

        cache.scheduleRetry(dueJson, pastTime)
        cache.scheduleRetry(futureJson, futureTime)

        // When
        val popped = cache.popDueToInflight(10)

        // Then
        assertEquals(1, popped.size)
        assertEquals(1L, cache.zsetSize(), "Future item should remain in queue")
        assertEquals(1L, cache.inflightSize(), "Only due item in inflight")
    }

    // ==================== Inflight Management Tests ====================

    @Test
    fun `removeFromInflight should remove specific item`() {
        // Given
        val json = """{"eventId":"evt-123"}"""
        val now = System.currentTimeMillis().toDouble()
        cache.scheduleRetry(json, now - 1000)
        val popped = cache.popDueToInflight(10)
        assertEquals(1L, cache.inflightSize())

        // When
        cache.removeFromInflight(popped[0])

        // Then
        assertEquals(0L, cache.inflightSize())
    }

    @Test
    fun `reclaimInflight should move stale items back to queue`() {
        // Given
        val json = """{"eventId":"stale"}"""
        val now = System.currentTimeMillis().toDouble()
        cache.scheduleRetry(json, now - 1000)
        cache.popDueToInflight(10)
        
        assertEquals(0L, cache.zsetSize())
        assertEquals(1L, cache.inflightSize())

        // Wait to make it stale
        Thread.sleep(100)

        // When - reclaim items older than 50ms
        cache.reclaimInflight(olderThanMs = 50)

        // Then
        assertEquals(1L, cache.zsetSize(), "Item should be back in queue")
        assertEquals(0L, cache.inflightSize(), "Inflight should be empty")
    }

    @Test
    fun `reclaimInflight should not reclaim fresh items`() {
        // Given
        val json = """{"eventId":"fresh"}"""
        val now = System.currentTimeMillis().toDouble()
        cache.scheduleRetry(json, now - 1000)
        cache.popDueToInflight(10)

        // When - reclaim items older than 10 seconds (none should qualify)
        cache.reclaimInflight(olderThanMs = 10_000)

        // Then
        assertEquals(0L, cache.zsetSize(), "Queue should still be empty")
        assertEquals(1L, cache.inflightSize(), "Item should remain inflight")
    }

    // ==================== Atomicity and Concurrency Tests ====================

    @Test
    fun `popDueToInflight should be atomic - no duplicates in concurrent access`() {
        // Given
        val count = 100
        val now = System.currentTimeMillis().toDouble()
        
        // Schedule 100 due items
        repeat(count) { i ->
            cache.scheduleRetry("""{"eventId":"evt-$i"}""", now - 1000)
        }

        val allPopped = mutableListOf<ByteArray>()
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        // When - 10 threads concurrently pop items
        repeat(threadCount) {
            executor.submit {
                try {
                    val popped = cache.popDueToInflight(10)
                    synchronized(allPopped) {
                        allPopped.addAll(popped)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        // Then - verify atomicity
        assertEquals(count, allPopped.size, "Should pop exactly $count items")
        assertEquals(0L, cache.zsetSize(), "Queue should be empty")
        assertEquals(count.toLong(), cache.inflightSize(), "All items in inflight")

        // Verify no duplicates
        val jsonStrings = allPopped.map { String(it) }.toSet()
        assertEquals(count, jsonStrings.size, "No duplicates should exist")
    }

    @Test
    fun `concurrent scheduleRetry should maintain queue integrity`() {
        // Given
        val threadCount = 50
        val itemsPerThread = 20
        val totalItems = threadCount * itemsPerThread
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        // When - multiple threads schedule retries
        repeat(threadCount) { t ->
            executor.submit {
                try {
                    repeat(itemsPerThread) { i ->
                        val json = """{"threadId":$t,"item":$i}"""
                        val retryAt = (System.currentTimeMillis() - 1000).toDouble()
                        cache.scheduleRetry(json, retryAt)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS))
        executor.shutdown()

        // Then
        val size = cache.zsetSize()
        assertEquals(totalItems.toLong(), size, "All items should be queued")
    }

    @Test
    fun `zsetSize should reflect accurate count with real Redis`() {
        // Given
        val now = System.currentTimeMillis().toDouble()
        
        assertEquals(0L, cache.zsetSize())

        // When - add items
        cache.scheduleRetry("""{"id":1}""", now)
        assertEquals(1L, cache.zsetSize())

        cache.scheduleRetry("""{"id":2}""", now)
        assertEquals(2L, cache.zsetSize())

        cache.scheduleRetry("""{"id":3}""", now)
        assertEquals(3L, cache.zsetSize())

        // When - remove one
        cache.popDueToInflight(1)
        assertEquals(2L, cache.zsetSize())
    }

    @Test
    fun `multiple reclaim cycles should work correctly`() {
        // Given
        val json1 = """{"eventId":"evt-1"}"""
        val json2 = """{"eventId":"evt-2"}"""
        val now = System.currentTimeMillis().toDouble()

        cache.scheduleRetry(json1, now - 1000)
        cache.scheduleRetry(json2, now - 1000)
        cache.popDueToInflight(10)

        assertEquals(2L, cache.inflightSize())

        // First reclaim
        Thread.sleep(100)
        cache.reclaimInflight(50)
        assertEquals(2L, cache.zsetSize())
        assertEquals(0L, cache.inflightSize())

        // Pop again
        cache.popDueToInflight(10)
        assertEquals(0L, cache.zsetSize())
        assertEquals(2L, cache.inflightSize())

        // Second reclaim
        Thread.sleep(100)
        cache.reclaimInflight(50)
        assertEquals(2L, cache.zsetSize())
        assertEquals(0L, cache.inflightSize())
    }

    @Test
    fun `different payment orders should have independent retry counters`() {
        // Given
        val id1 = 100L
        val id2 = 200L

        // When
        cache.incrementAndGetRetryCount(id1)
        cache.incrementAndGetRetryCount(id1)
        cache.incrementAndGetRetryCount(id2)

        // Then
        assertEquals(2, cache.getRetryCount(id1))
        assertEquals(1, cache.getRetryCount(id2))

        // When - reset one
        cache.resetRetryCounter(id1)

        // Then
        assertEquals(0, cache.getRetryCount(id1))
        assertEquals(1, cache.getRetryCount(id2))
    }
}

