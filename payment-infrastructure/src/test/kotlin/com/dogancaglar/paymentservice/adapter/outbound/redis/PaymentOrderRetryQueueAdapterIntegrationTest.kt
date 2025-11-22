package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import com.dogancaglar.common.time.Utc
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Integration tests for PaymentOrderRetryQueueAdapter with real Redis (Testcontainers).
 * 
 * These tests validate:
 * - End-to-end retry scheduling and polling
 * - Real serialization/deserialization
 * - Inflight management with real Redis
 * - Concurrent polling behaviorcle
 * - Poison message handling
 * 
 * Tagged as @integration for selective execution.
 */
@Tag("integration")
@SpringBootTest(classes = [PaymentOrderRetryQueueAdapterIntegrationTest.TestConfig::class])
@Testcontainers
class PaymentOrderRetryQueueAdapterIntegrationTest {

    @Configuration
    @Import(RedisAutoConfiguration::class, PaymentOrderRetryRedisCache::class)
    class TestConfig {
        @Bean
        fun objectMapper(): ObjectMapper {
            return ObjectMapper().apply {
                registerModule(JavaTimeModule())
                registerKotlinModule()
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }

        @Bean
        fun paymentOrderRetryQueueAdapter(
            cache: PaymentOrderRetryRedisCache,
            objectMapper: ObjectMapper
        ): PaymentOrderRetryQueueAdapter {
            return PaymentOrderRetryQueueAdapter(
                cache,
                SimpleMeterRegistry(),
                objectMapper,
                PaymentOrderDomainEventMapper()
            )
        }
    }

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
    private lateinit var adapter: PaymentOrderRetryQueueAdapter

    @Autowired
    private lateinit var cache: PaymentOrderRetryRedisCache

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun setUp() {
        redisTemplate.connectionFactory?.connection?.flushAll()
        
        // Mock EventLogContext
        mockkObject(EventLogContext)
        every { EventLogContext.getTraceId() } returns "test-trace-id"
        every { EventLogContext.getEventId() } returns null
    }
    
    @AfterEach
    fun tearDown() {
        unmockkObject(EventLogContext)
    }

    private fun createTestPaymentOrder(
        id: Long = 123L,
        retryCount: Int = 0,
        status: PaymentOrderStatus = PaymentOrderStatus.CAPTURE_REQUESTED,
        createdAt: Instant = Utc.nowInstant(),
        updatedAt: Instant = createdAt
    ): PaymentOrder =
        PaymentOrder.rehydrate(
            paymentOrderId = PaymentOrderId(id),
            paymentId = PaymentId(999L),
            sellerId = SellerId("111"),
            amount = Amount.of(10000L, Currency("USD")),
            status = status,
            retryCount = retryCount,
            createdAt = Utc.fromInstant(createdAt),
            updatedAt = Utc.fromInstant(updatedAt)
        )

    // ==================== End-to-End Retry Flow ====================

    @Test
    fun `should schedule and poll retry with real Redis`() {
        // Given
        val paymentOrder = createTestPaymentOrder(id = 123L, retryCount = 1, status = PaymentOrderStatus.CAPTURE_REQUESTED)
        val backOffMillis = 100L // Short delay for testing

        // When - schedule retry
        adapter.scheduleRetry(paymentOrder, backOffMillis)
        
        // Verify item was scheduled
        assertEquals(1L, cache.zsetSize(), "Item should be in queue after scheduling")

        // Wait for retry to become due
        Thread.sleep(150)

        // Then - poll should return the scheduled item
        val polled = adapter.pollDueRetriesToInflight(10)
        assertEquals(1, polled.size, "Should poll 1 item after delay")
        
        val retryItem = polled[0]
        assertEquals("123", retryItem.envelope.data.paymentOrderId)
        assertEquals(1, retryItem.envelope.data.attempt)
    }

    @Test
    fun `pollDueRetriesToInflight should not return future retries`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        val backOffMillis = 10_000L // 10 seconds in future

        // When - schedule future retry
        adapter.scheduleRetry(paymentOrder, backOffMillis)

        // Then - immediate poll should return nothing
        val polled = adapter.pollDueRetriesToInflight(10)
        assertTrue(polled.isEmpty())
    }

    @Test
    fun `should handle multiple retries for same payment order`() {
        // Given
        val paymentOrder1 = createTestPaymentOrder(id = 100L, retryCount = 1)
        val paymentOrder2 = createTestPaymentOrder(id = 100L, retryCount = 2)
        
        // When - schedule two retries for same order
        adapter.scheduleRetry(paymentOrder1, 100L)
        adapter.scheduleRetry(paymentOrder2, 100L)

        Thread.sleep(150)

        // Then - both should be polled
        val polled = adapter.pollDueRetriesToInflight(10)
        assertEquals(2, polled.size)
    }

    // ==================== Inflight Management ====================

    @Test
    fun `polled items should be in inflight queue`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        adapter.scheduleRetry(paymentOrder, 100L)
        Thread.sleep(150)

        assertEquals(0L, cache.inflightSize())

        // When
        val polled = adapter.pollDueRetriesToInflight(10)

        // Then
        assertEquals(1, polled.size)
        assertEquals(1L, cache.inflightSize())
        assertEquals(0L, cache.zsetSize())
    }

    @Test
    fun `removeFromInflight should remove item from inflight queue`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        adapter.scheduleRetry(paymentOrder, 100L)
        Thread.sleep(150)
        
        val polled = adapter.pollDueRetriesToInflight(10)
        assertEquals(1L, cache.inflightSize())

        // When
        adapter.removeFromInflight(polled[0].raw)

        // Then
        assertEquals(0L, cache.inflightSize())
    }

    @Test
    fun `reclaimInflight should move stale items back to queue`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        adapter.scheduleRetry(paymentOrder, 100L)
        Thread.sleep(150)
        
        adapter.pollDueRetriesToInflight(10)
        assertEquals(1L, cache.inflightSize())
        assertEquals(0L, cache.zsetSize())

        // Wait to make inflight items stale
        Thread.sleep(100)

        // When - reclaim items older than 50ms
        adapter.reclaimInflight(olderThanMs = 50)

        // Then - should be back in queue
        assertEquals(0L, cache.inflightSize())
        assertEquals(1L, cache.zsetSize())

        // And should be pollable again
        val repolled = adapter.pollDueRetriesToInflight(10)
        assertEquals(1, repolled.size)
    }

    // ==================== Retry Counter ====================

    @Test
    fun `retry counter operations should work with real Redis`() {
        // Given
        val paymentOrderId = PaymentOrderId(456L)

        // When/Then
        assertEquals(0, adapter.getRetryCount(paymentOrderId))
        
        cache.incrementAndGetRetryCount(paymentOrderId.value)
        assertEquals(1, adapter.getRetryCount(paymentOrderId))
        
        cache.incrementAndGetRetryCount(paymentOrderId.value)
        assertEquals(2, adapter.getRetryCount(paymentOrderId))
        
        adapter.resetRetryCounter(paymentOrderId)
        assertEquals(0, adapter.getRetryCount(paymentOrderId))
    }

    // ==================== Concurrency ====================

    @Test
    fun `concurrent polling should not duplicate items - ATOMICITY TEST`() {
        // Given
        val count = 100
        repeat(count) { i ->
            val paymentOrder = createTestPaymentOrder(id = i.toLong())
            adapter.scheduleRetry(paymentOrder, 100L)
        }

        Thread.sleep(150)

        val allPolled = ConcurrentHashMap.newKeySet<Long>()
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        // When - multiple threads poll concurrently
        repeat(threadCount) {
            executor.submit {
                try {
                    val polled = adapter.pollDueRetriesToInflight(10)
                    polled.forEach { item ->
                        allPolled.add(item.envelope.data.paymentOrderId.toLong())
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        // Then - all items polled exactly once (no duplicates)
        assertEquals(count, allPolled.size)
        assertEquals(0L, cache.zsetSize())
        assertEquals(count.toLong(), cache.inflightSize())
    }

    @Test
    fun `concurrent schedule and poll should maintain integrity`() {
        // Given
        val scheduleCount = 50
        val latch = CountDownLatch(scheduleCount + 1)
        val executor = Executors.newFixedThreadPool(10)

        // When - schedule from multiple threads
        repeat(scheduleCount) { i ->
            executor.submit {
                try {
                    val paymentOrder = createTestPaymentOrder(id = i.toLong())
                    adapter.scheduleRetry(paymentOrder, 50L)
                } finally {
                    latch.countDown()
                }
            }
        }

        // And poll concurrently
        val polled = ConcurrentHashMap.newKeySet<Long>()
        executor.submit {
            try {
                Thread.sleep(100) // Let some items become due
                val items = adapter.pollDueRetriesToInflight(100)
                items.forEach { polled.add(it.envelope.data.paymentOrderId.toLong()) }
            } finally {
                latch.countDown()
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        // Then - verify consistency
        val totalInSystem = cache.zsetSize() + cache.inflightSize()
        assertEquals(scheduleCount.toLong(), totalInSystem)
        
        // All polled items should be unique
        assertEquals(polled.size, polled.size) // No duplicates
    }

    // ==================== Error Handling ====================

    @Test
    fun `should handle poison messages gracefully in real Redis`() {
        // Given - manually inject invalid JSON into Redis
        val validOrder = createTestPaymentOrder(id = 100L)
        adapter.scheduleRetry(validOrder, 100L)

        // Inject poison message directly into Redis
        val poisonJson = "{ invalid json }"
        val now = System.currentTimeMillis().toDouble()
        redisTemplate.opsForZSet().add("payment_retry_queue", poisonJson, now - 1000)

        Thread.sleep(150)

        // When - poll (should handle poison message)
        val polled = adapter.pollDueRetriesToInflight(10)

        // Then - only valid message returned, poison message removed from inflight
        assertEquals(1, polled.size)
        assertEquals("100", polled[0].envelope.data.paymentOrderId)
        
        // Verify inflight size (poison message was removed)
        assertEquals(1L, cache.inflightSize())
    }

    @Test
    fun `should preserve event envelope structure through Redis roundtrip`() {
        // Given
        val paymentOrder = createTestPaymentOrder(id = 789L, retryCount = 3)

        // When
        adapter.scheduleRetry(paymentOrder, 100L)
        Thread.sleep(150)
        val polled = adapter.pollDueRetriesToInflight(10)

        // Then - verify envelope structure
        val env = polled[0].envelope
        assertNotNull(env.eventId)
        assertEquals("payment_order_capture_requested", env.eventType)
        assertEquals("789", env.aggregateId)
        assertNotNull(env.traceId)
        assertNotNull(env.timestamp)

        // Verify data
        val data = env.data

        assertEquals("789", data.paymentOrderId)

        val expectedPublicPaymentOrderId = PaymentOrderId(789L).toPublicPaymentOrderId()
        assertEquals(expectedPublicPaymentOrderId, data.publicPaymentOrderId)

        assertEquals(3, data.attempt)
    }
}

