package com.dogancaglar.paymentservice.adapter.outbound.redis

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

/**
 * Unit tests for RedisIdGeneratorPortAdapter using MockK.
 * 
 * Tests cover:
 * - ID generation behavior
 * - Namespace isolation logic
 * - setMinValue behavior  
 * - Error handling
 * - Edge cases with null values
 */
class RedisIdGeneratorPortAdapterTest {

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOperations: ValueOperations<String, String>
    private lateinit var adapter: RedisIdGeneratorPortAdapter

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk()
        valueOperations = mockk()
        every { redisTemplate.opsForValue() } returns valueOperations
        adapter = RedisIdGeneratorPortAdapter(redisTemplate)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `nextId should return incremented value from Redis`() {
        // Given
        val namespace = "id-generator:payment"
        every { valueOperations.increment(namespace) } returns 42L

        // When
        val result = adapter.nextId(namespace)

        // Then
        assertEquals(42L, result)
        verify(exactly = 1) { valueOperations.increment(namespace) }
    }

    @Test
    fun `nextId should generate sequential IDs for same namespace`() {
        // Given
        val namespace = "test-namespace"
        every { valueOperations.increment(namespace) } returns 1L andThen 2L andThen 3L

        // When
        val id1 = adapter.nextId(namespace)
        val id2 = adapter.nextId(namespace)
        val id3 = adapter.nextId(namespace)

        // Then
        assertEquals(1L, id1)
        assertEquals(2L, id2)
        assertEquals(3L, id3)
        verify(exactly = 3) { valueOperations.increment(namespace) }
    }

    @Test
    fun `nextId should handle different namespaces independently`() {
        // Given
        val namespace1 = "id-generator:payment"
        val namespace2 = "id-generator:payment-order"
        
        every { valueOperations.increment(namespace1) } returns 1L andThen 2L
        every { valueOperations.increment(namespace2) } returns 1L andThen 2L

        // When
        val payment1 = adapter.nextId(namespace1)
        val order1 = adapter.nextId(namespace2)
        val payment2 = adapter.nextId(namespace1)
        val order2 = adapter.nextId(namespace2)

        // Then
        assertEquals(1L, payment1)
        assertEquals(1L, order1)
        assertEquals(2L, payment2)
        assertEquals(2L, order2)
        
        verify(exactly = 2) { valueOperations.increment(namespace1) }
        verify(exactly = 2) { valueOperations.increment(namespace2) }
    }

    @Test
    fun `nextId should throw exception when Redis returns null`() {
        // Given
        val namespace = "failing-namespace"
        every { valueOperations.increment(namespace) } returns null

        // When & Then
        val exception = assertThrows(IllegalStateException::class.java) {
            adapter.nextId(namespace)
        }
        
        assertEquals("Redis ID generation failed for namespace: $namespace", exception.message)
        verify(exactly = 1) { valueOperations.increment(namespace) }
    }

    @Test
    fun `nextId should handle large ID values`() {
        // Given
        val namespace = "large-id-test"
        val largeValue = Long.MAX_VALUE - 10
        every { valueOperations.increment(namespace) } returns largeValue

        // When
        val result = adapter.nextId(namespace)

        // Then
        assertEquals(largeValue, result)
        verify(exactly = 1) { valueOperations.increment(namespace) }
    }

    @Test
    fun `getRawValue should return null when key doesn't exist`() {
        // Given
        val namespace = "non-existent"
        every { valueOperations.get(namespace) } returns null

        // When
        val result = adapter.getRawValue(namespace)

        // Then
        assertNull(result)
        verify(exactly = 1) { valueOperations.get(namespace) }
    }

    @Test
    fun `getRawValue should return parsed long value`() {
        // Given
        val namespace = "existing-key"
        every { valueOperations.get(namespace) } returns "12345"

        // When
        val result = adapter.getRawValue(namespace)

        // Then
        assertEquals(12345L, result)
        verify(exactly = 1) { valueOperations.get(namespace) }
    }

    @Test
    fun `getRawValue should return null for non-numeric value`() {
        // Given
        val namespace = "invalid-key"
        every { valueOperations.get(namespace) } returns "not-a-number"

        // When
        val result = adapter.getRawValue(namespace)

        // Then
        assertNull(result)
        verify(exactly = 1) { valueOperations.get(namespace) }
    }

    @Test
    fun `getRawValue should handle empty string`() {
        // Given
        val namespace = "empty-key"
        every { valueOperations.get(namespace) } returns ""

        // When
        val result = adapter.getRawValue(namespace)

        // Then
        assertNull(result)
        verify(exactly = 1) { valueOperations.get(namespace) }
    }

    @Test
    fun `setMinValue should set new value when current is null`() {
        // Given
        val namespace = "new-namespace"
        val minValue = 1000L
        every { valueOperations.get(namespace) } returns null
        every { valueOperations.set(namespace, "1000") } returns Unit

        // When
        adapter.setMinValue(namespace, minValue)

        // Then
        verify(exactly = 1) { valueOperations.get(namespace) }
        verify(exactly = 1) { valueOperations.set(namespace, "1000") }
    }

    @Test
    fun `setMinValue should update value when new value is greater`() {
        // Given
        val namespace = "update-namespace"
        every { valueOperations.get(namespace) } returns "100"
        every { valueOperations.set(namespace, "500") } returns Unit

        // When
        adapter.setMinValue(namespace, 500)

        // Then
        verify(exactly = 1) { valueOperations.get(namespace) }
        verify(exactly = 1) { valueOperations.set(namespace, "500") }
    }

    @Test
    fun `setMinValue should not update when new value is lower`() {
        // Given
        val namespace = "no-update-namespace"
        every { valueOperations.get(namespace) } returns "1000"

        // When
        adapter.setMinValue(namespace, 500)

        // Then
        verify(exactly = 1) { valueOperations.get(namespace) }
        verify(exactly = 0) { valueOperations.set(any(), any()) }
    }

    @Test
    fun `setMinValue should not update when new value equals current value`() {
        // Given
        val namespace = "equal-value-namespace"
        every { valueOperations.get(namespace) } returns "1000"

        // When
        adapter.setMinValue(namespace, 1000)

        // Then
        verify(exactly = 1) { valueOperations.get(namespace) }
        verify(exactly = 0) { valueOperations.set(any(), any()) }
    }

    @Test
    fun `setMinValue should handle non-numeric current value as zero`() {
        // Given
        val namespace = "invalid-current-namespace"
        every { valueOperations.get(namespace) } returns "invalid"
        every { valueOperations.set(namespace, "1000") } returns Unit

        // When
        adapter.setMinValue(namespace, 1000)

        // Then
        verify(exactly = 1) { valueOperations.get(namespace) }
        verify(exactly = 1) { valueOperations.set(namespace, "1000") }
    }

    @Test
    fun `setMinValue should work with actual IdNamespaces constants`() {
        // Given - simulating real namespace usage
        val paymentNamespace = "id-generator:payment"
        val minPaymentId = 100000L
        
        every { valueOperations.get(paymentNamespace) } returns "50000"
        every { valueOperations.set(paymentNamespace, "100000") } returns Unit

        // When
        adapter.setMinValue(paymentNamespace, minPaymentId)

        // Then
        verify(exactly = 1) { valueOperations.get(paymentNamespace) }
        verify(exactly = 1) { valueOperations.set(paymentNamespace, "100000") }
    }

    @Test
    fun `setMinValue should not set when value equals zero and current is null`() {
        // Given
        val namespace = "zero-value-namespace"
        every { valueOperations.get(namespace) } returns null

        // When
        adapter.setMinValue(namespace, 0)

        // Then - Current is null (treated as 0), new value is 0, so 0 > 0 is false, no update
        verify(exactly = 1) { valueOperations.get(namespace) }
        verify(exactly = 0) { valueOperations.set(any(), any()) }
    }

    @Test
    fun `setMinValue should set when value is positive and current is null`() {
        // Given
        val namespace = "positive-value-namespace"
        every { valueOperations.get(namespace) } returns null
        every { valueOperations.set(namespace, "100") } returns Unit

        // When
        adapter.setMinValue(namespace, 100)

        // Then - Current is null (treated as 0), 100 > 0, so should set
        verify(exactly = 1) { valueOperations.get(namespace) }
        verify(exactly = 1) { valueOperations.set(namespace, "100") }
    }

    @Test
    fun `setMinValue should not update when both current and new value are zero`() {
        // Given
        val namespace = "zero-value-namespace"
        every { valueOperations.get(namespace) } returns "0"

        // When
        adapter.setMinValue(namespace, 0)

        // Then - 0 is not greater than 0, so no update
        verify(exactly = 1) { valueOperations.get(namespace) }
        verify(exactly = 0) { valueOperations.set(any(), any()) }
    }

    @Test
    fun `setMinValue should handle negative current value`() {
        // Given
        val namespace = "negative-current-namespace"
        every { valueOperations.get(namespace) } returns "-100"
        every { valueOperations.set(namespace, "1000") } returns Unit

        // When
        adapter.setMinValue(namespace, 1000)

        // Then
        verify(exactly = 1) { valueOperations.get(namespace) }
        verify(exactly = 1) { valueOperations.set(namespace, "1000") }
    }

    @Test
    fun `nextId should work correctly for multiple rapid calls`() {
        // Given
        val namespace = "rapid-calls-namespace"
        val ids = (1L..100L).toList()
        every { valueOperations.increment(namespace) } returnsMany ids

        // When
        val results = (1..100).map { adapter.nextId(namespace) }

        // Then
        assertEquals(ids, results)
        verify(exactly = 100) { valueOperations.increment(namespace) }
    }
}
