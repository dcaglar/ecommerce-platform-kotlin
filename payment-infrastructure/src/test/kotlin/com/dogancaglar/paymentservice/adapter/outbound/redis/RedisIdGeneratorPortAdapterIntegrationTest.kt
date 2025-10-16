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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration tests for RedisIdGeneratorPortAdapter with real Redis (Testcontainers).
 * 
 * These tests validate:
 * - Atomicity of Redis INCR command under concurrent load
 * - Real Redis behavior (not mocked)
 * - Connection handling
 * - Large volume scenarios
 * 
 * Tagged as @IntegrationTest for selective execution:
 * - mvn test                             -> Runs ALL tests (unit + integration)
 * - mvn test -Dgroups=integration        -> Runs integration tests only
 * - mvn test -DexcludedGroups=integration -> Runs unit tests only (fast)
 */
@Tag("integration")
@SpringBootTest(classes = [RedisIdGeneratorPortAdapterIntegrationTest.TestConfig::class])
@Testcontainers
class RedisIdGeneratorPortAdapterIntegrationTest {

    @Configuration
    @Import(RedisAutoConfiguration::class, RedisIdGeneratorPortAdapter::class)
    class TestConfig

    companion object {
        @Container
        @JvmStatic
        val redisContainer = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(false) // Don't reuse for integration tests - clean state each time

        @DynamicPropertySource
        @JvmStatic
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.firstMappedPort }
        }
    }

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var adapter: RedisIdGeneratorPortAdapter

    @BeforeEach
    fun setUp() {
        // Clean up Redis before each test
        redisTemplate.connectionFactory?.connection?.flushAll()
    }

    @Test
    fun `nextId should generate sequential IDs with real Redis`() {
        // Given
        val namespace = "integration-test:payment"

        // When
        val id1 = adapter.nextId(namespace)
        val id2 = adapter.nextId(namespace)
        val id3 = adapter.nextId(namespace)

        // Then
        assertEquals(1L, id1)
        assertEquals(2L, id2)
        assertEquals(3L, id3)
    }

    @Test
    fun `concurrent nextId calls should generate unique sequential IDs - ATOMICITY TEST`() {
        // Given
        val namespace = "concurrent-test:payment"
        val threadCount = 100
        val iterationsPerThread = 10
        val totalExpectedIds = threadCount * iterationsPerThread
        
        val allGeneratedIds = ConcurrentHashMap.newKeySet<Long>()
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val exceptions = AtomicInteger(0)

        // When - spawn multiple threads generating IDs concurrently
        repeat(threadCount) {
            executor.submit {
                try {
                    repeat(iterationsPerThread) {
                        val id = adapter.nextId(namespace)
                        allGeneratedIds.add(id)
                    }
                } catch (e: Exception) {
                    exceptions.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Threads did not complete in time")
        executor.shutdown()
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor did not shut down")

        // Then - verify atomicity guarantees
        assertEquals(0, exceptions.get(), "No exceptions should occur")
        assertEquals(totalExpectedIds, allGeneratedIds.size, "All IDs should be unique")
        
        // Verify IDs are sequential (1 to totalExpectedIds)
        val sortedIds = allGeneratedIds.sorted()
        assertEquals((1L..totalExpectedIds).toList(), sortedIds, 
            "IDs should be sequential from 1 to $totalExpectedIds without gaps")
        
        // Verify final counter value in Redis
        val finalValue = adapter.getRawValue(namespace)
        assertEquals(totalExpectedIds.toLong(), finalValue, 
            "Final Redis counter should equal total IDs generated")
    }

    @Test
    fun `multiple adapters sharing same Redis should see consistent counters`() {
        // Given - simulate multiple service instances
        val adapter1 = RedisIdGeneratorPortAdapter(redisTemplate)
        val adapter2 = RedisIdGeneratorPortAdapter(redisTemplate)
        val namespace = "shared-counter-test"

        // When - alternate between adapters
        val id1 = adapter1.nextId(namespace)
        val id2 = adapter2.nextId(namespace) // Different adapter instance
        val id3 = adapter1.nextId(namespace)
        val id4 = adapter2.nextId(namespace)

        // Then - counter is shared across instances
        assertEquals(1L, id1)
        assertEquals(2L, id2)
        assertEquals(3L, id3)
        assertEquals(4L, id4)
    }

    @Test
    fun `should handle high volume ID generation - performance test`() {
        // Given
        val namespace = "high-volume-test"
        val count = 10000

        // When - generate many IDs
        val startTime = System.currentTimeMillis()
        val ids = (1..count).map { adapter.nextId(namespace) }
        val duration = System.currentTimeMillis() - startTime

        // Then
        assertEquals(count, ids.size)
        assertEquals(count, ids.toSet().size, "All IDs should be unique")
        assertEquals((1L..count).toList(), ids, "IDs should be sequential")
        
        // Performance assertion (should be fast with Redis)
        assertTrue(duration < 5000, "Generating $count IDs took ${duration}ms, should be under 5 seconds")
        println("✅ Generated $count IDs in ${duration}ms (${count * 1000 / duration} IDs/sec)")
    }

    @Test
    fun `getRawValue should return actual Redis value`() {
        // Given
        val namespace = "raw-value-test"
        
        // When - no value exists
        val initial = adapter.getRawValue(namespace)
        
        // Then
        assertNull(initial)
        
        // When - generate some IDs
        adapter.nextId(namespace)
        adapter.nextId(namespace)
        adapter.nextId(namespace)
        val current = adapter.getRawValue(namespace)
        
        // Then
        assertEquals(3L, current)
    }

    @Test
    fun `setMinValue should actually set Redis value when greater`() {
        // Given
        val namespace = "set-min-test"
        adapter.nextId(namespace) // counter = 1
        adapter.nextId(namespace) // counter = 2
        
        // When
        adapter.setMinValue(namespace, 1000)
        
        // Then - verify in Redis
        val rawValue = redisTemplate.opsForValue().get(namespace)
        assertEquals("1000", rawValue)
        
        // And next ID should be 1001
        val nextId = adapter.nextId(namespace)
        assertEquals(1001L, nextId)
    }

    @Test
    fun `setMinValue should not decrease existing counter`() {
        // Given
        val namespace = "no-decrease-test"
        adapter.setMinValue(namespace, 1000)
        val after1000 = adapter.getRawValue(namespace)
        assertEquals(1000L, after1000)
        
        // When - try to set lower
        adapter.setMinValue(namespace, 100)
        
        // Then - value should remain 1000
        val stillValue = adapter.getRawValue(namespace)
        assertEquals(1000L, stillValue)
    }

    @Test
    fun `different namespaces should have independent counters in real Redis`() {
        // Given
        val namespace1 = "integration:payment"
        val namespace2 = "integration:payment-order"

        // When
        val payment1 = adapter.nextId(namespace1)
        val order1 = adapter.nextId(namespace2)
        val payment2 = adapter.nextId(namespace1)
        val order2 = adapter.nextId(namespace2)

        // Then - verify independence
        assertEquals(1L, payment1)
        assertEquals(1L, order1)
        assertEquals(2L, payment2)
        assertEquals(2L, order2)
        
        // Verify in Redis directly
        assertEquals("2", redisTemplate.opsForValue().get(namespace1))
        assertEquals("2", redisTemplate.opsForValue().get(namespace2))
    }

    @Test
    fun `should handle Redis INCR overflow behavior correctly`() {
        // Given
        val namespace = "overflow-test"
        val nearMax = Long.MAX_VALUE - 5
        
        // When - set counter near Long.MAX_VALUE
        adapter.setMinValue(namespace, nearMax)
        
        // Then - verify it was set
        assertEquals(nearMax, adapter.getRawValue(namespace))
        
        // And we can still increment
        val id1 = adapter.nextId(namespace)
        val id2 = adapter.nextId(namespace)
        
        assertEquals(nearMax + 1, id1)
        assertEquals(nearMax + 2, id2)
    }

    @Test
    fun `concurrent setMinValue and nextId should be safe`() {
        // Given
        val namespace = "concurrent-mixed-test"
        val threadCount = 50
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val allIds = ConcurrentHashMap.newKeySet<Long>()

        // When - mix setMinValue and nextId calls concurrently
        repeat(threadCount / 2) { i ->
            // Half threads do nextId
            executor.submit {
                try {
                    val id = adapter.nextId(namespace)
                    allIds.add(id)
                } finally {
                    latch.countDown()
                }
            }
            
            // Half threads do setMinValue
            executor.submit {
                try {
                    adapter.setMinValue(namespace, (i * 10).toLong())
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for completion
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        executor.shutdown()

        // Then - all IDs should be unique (no duplicates despite concurrent setMinValue)
        assertEquals(threadCount / 2, allIds.size, "All nextId calls should produce unique IDs")
        
        // All IDs should be positive
        assertTrue(allIds.all { it > 0 }, "All IDs should be positive")
    }

    @Test
    fun `adapter should work with actual IdNamespaces constants`() {
        // Given - real namespace constants from the application
        val paymentNamespace = "id-generator:payment"
        val orderNamespace = "id-generator:payment-order"
        
        // When
        val paymentId1 = adapter.nextId(paymentNamespace)
        val paymentId2 = adapter.nextId(paymentNamespace)
        val orderId1 = adapter.nextId(orderNamespace)
        
        // Then
        assertEquals(1L, paymentId1)
        assertEquals(2L, paymentId2)
        assertEquals(1L, orderId1)
        
        // Verify in Redis with actual keys
        assertEquals("2", redisTemplate.opsForValue().get(paymentNamespace))
        assertEquals("1", redisTemplate.opsForValue().get(orderNamespace))
    }

    @Test
    fun `should handle Redis commands correctly - no mock behavior`() {
        // This test validates we're using real Redis, not mocks
        // by checking actual Redis data structure operations
        
        val namespace = "real-redis-test"
        
        // Direct Redis operations
        redisTemplate.opsForValue().set(namespace, "100")
        assertEquals("100", redisTemplate.opsForValue().get(namespace))
        
        // Through adapter
        val id = adapter.nextId(namespace)
        assertEquals(101L, id, "INCR on '100' should return 101")
        
        // Verify Redis incremented the value
        assertEquals("101", redisTemplate.opsForValue().get(namespace))
    }
}

