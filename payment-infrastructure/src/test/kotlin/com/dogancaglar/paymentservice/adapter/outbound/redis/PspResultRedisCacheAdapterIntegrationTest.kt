package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
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
import java.util.concurrent.TimeUnit

/**
 * Integration tests for PspResultRedisCacheAdapter with real Redis (Testcontainers).
 * 
 * These tests validate:
 * - Real Redis caching behavior
 * - TTL expiration
 * - Key prefixing
 * - Concurrent access
 * 
 * Tagged as @integration for selective execution:
 * - mvn test                             -> Runs ALL tests (unit + integration)
 * - mvn test -Dgroups=integration        -> Runs integration tests only
 * - mvn test -DexcludedGroups=integration -> Runs unit tests only (fast)
 */
@Tag("integration")
@SpringBootTest(classes = [PspResultRedisCacheAdapterIntegrationTest.TestConfig::class])
@Testcontainers
class PspResultRedisCacheAdapterIntegrationTest {

    @Configuration
    @Import(RedisAutoConfiguration::class)
    class TestConfig {
        @Bean
        fun pspResultRedisCacheAdapter(redisTemplate: StringRedisTemplate): PspResultRedisCacheAdapter {
            return PspResultRedisCacheAdapter(redisTemplate, ttlSeconds = 2) // Short TTL for testing
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
    private lateinit var adapter: PspResultRedisCacheAdapter

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun setUp() {
        // Clean up Redis before each test
        redisTemplate.connectionFactory?.connection?.flushAll()
    }

    @Test
    fun `put and get should work with real Redis`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val resultJson = """{"status":"SUCCESS","transactionId":"tx-456"}"""

        // When
        adapter.put(paymentOrderId, resultJson)
        val retrieved = adapter.get(paymentOrderId)

        // Then
        assertEquals(resultJson, retrieved)
    }

    @Test
    fun `get should return null for non-existent key`() {
        // Given
        val paymentOrderId = PaymentOrderId(999L)

        // When
        val result = adapter.get(paymentOrderId)

        // Then
        assertNull(result)
    }

    @Test
    fun `remove should delete key from Redis`() {
        // Given
        val paymentOrderId = PaymentOrderId(456L)
        val resultJson = """{"status":"FAILED"}"""
        adapter.put(paymentOrderId, resultJson)

        // When
        adapter.remove(paymentOrderId)
        val result = adapter.get(paymentOrderId)

        // Then
        assertNull(result)
    }

    @Test
    fun `TTL should expire cached results after configured time`() {
        // Given
        val paymentOrderId = PaymentOrderId(789L)
        val resultJson = """{"status":"PENDING"}"""
        adapter.put(paymentOrderId, resultJson)

        // Verify it exists immediately
        assertNotNull(adapter.get(paymentOrderId))

        // When - wait for TTL to expire (2 seconds + buffer)
        Thread.sleep(2500)

        // Then - should be expired
        val result = adapter.get(paymentOrderId)
        assertNull(result, "Result should be expired after TTL")
    }

    @Test
    fun `multiple put operations should overwrite previous values`() {
        // Given
        val paymentOrderId = PaymentOrderId(111L)
        val json1 = """{"status":"PENDING"}"""
        val json2 = """{"status":"SUCCESS"}"""

        // When
        adapter.put(paymentOrderId, json1)
        val intermediate = adapter.get(paymentOrderId)
        
        adapter.put(paymentOrderId, json2)
        val final = adapter.get(paymentOrderId)

        // Then
        assertEquals(json1, intermediate)
        assertEquals(json2, final)
    }

    @Test
    fun `adapter should handle concurrent put and get operations`() {
        // Given
        val paymentOrderId = PaymentOrderId(222L)
        val resultJson = """{"status":"SUCCESS"}"""
        val threads = 10
        val iterations = 100

        // When - multiple threads putting and getting
        val putThread = Thread {
            repeat(iterations) {
                adapter.put(paymentOrderId, resultJson)
            }
        }

        val getThreads = (1..threads).map {
            Thread {
                repeat(iterations) {
                    val result = adapter.get(paymentOrderId)
                    // Result should be either null or the expected JSON
                    if (result != null) {
                        assertEquals(resultJson, result)
                    }
                }
            }
        }

        // Start all threads
        putThread.start()
        getThreads.forEach { it.start() }

        // Wait for completion
        putThread.join(5000)
        getThreads.forEach { it.join(5000) }

        // Then - final state should be consistent
        val finalResult = adapter.get(paymentOrderId)
        assertEquals(resultJson, finalResult)
    }

    @Test
    fun `adapter should verify key prefix is used in Redis`() {
        // Given
        val paymentOrderId = PaymentOrderId(333L)
        val resultJson = """{"status":"SUCCESS"}"""
        val expectedRedisKey = "psp_result:333"

        // When
        adapter.put(paymentOrderId, resultJson)

        // Then - verify directly in Redis with the prefix
        val directRedisValue = redisTemplate.opsForValue().get(expectedRedisKey)
        assertEquals(resultJson, directRedisValue)
    }

    @Test
    fun `TTL should be reset on subsequent put operations`() {
        // Given
        val paymentOrderId = PaymentOrderId(444L)
        val resultJson = """{"status":"SUCCESS"}"""
        
        // When - put, wait 1 second, put again
        adapter.put(paymentOrderId, resultJson)
        Thread.sleep(1000)
        adapter.put(paymentOrderId, resultJson) // Reset TTL
        Thread.sleep(1500) // Total: 2.5 seconds from first put, 1.5 from second

        // Then - should still exist because TTL was reset
        val result = adapter.get(paymentOrderId)
        assertNotNull(result, "Result should still exist because TTL was reset")
    }

    @Test
    fun `adapter should handle large JSON payloads with real Redis`() {
        // Given
        val paymentOrderId = PaymentOrderId(555L)
        val largePayload = """{"data":"${"x".repeat(100000)}"}"""

        // When
        adapter.put(paymentOrderId, largePayload)
        val retrieved = adapter.get(paymentOrderId)

        // Then
        assertEquals(largePayload, retrieved)
    }

    @Test
    fun `different payment order IDs should have independent cache entries`() {
        // Given
        val id1 = PaymentOrderId(100L)
        val id2 = PaymentOrderId(200L)
        val json1 = """{"status":"SUCCESS","id":100}"""
        val json2 = """{"status":"FAILED","id":200}"""

        // When
        adapter.put(id1, json1)
        adapter.put(id2, json2)

        // Then - each ID has its own cached value
        assertEquals(json1, adapter.get(id1))
        assertEquals(json2, adapter.get(id2))

        // When - remove one
        adapter.remove(id1)

        // Then - only the removed one is gone
        assertNull(adapter.get(id1))
        assertEquals(json2, adapter.get(id2))
    }
}

