package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

/**
 * Unit tests for PspResultRedisCacheAdapter using MockK.
 * 
 * Tests verify:
 * - Cache put/get/remove operations
 * - TTL configuration
 * - Key prefixing
 * - Null handling
 */
class PspResultRedisCacheAdapterTest {

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOperations: ValueOperations<String, String>
    private lateinit var adapter: PspResultRedisCacheAdapter
    private val ttlSeconds = 3600L

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk(relaxed = true)
        valueOperations = mockk(relaxed = true)
        every { redisTemplate.opsForValue() } returns valueOperations
        adapter = PspResultRedisCacheAdapter(redisTemplate, ttlSeconds)
    }

    @Test
    fun `put should store result with correct key prefix and TTL`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val resultJson = """{"status":"SUCCESS","transactionId":"tx-456"}"""
        val expectedKey = "psp_result:123"

        // When
        adapter.put(paymentOrderId, resultJson)

        // Then
        verify(exactly = 1) {
            valueOperations.set(expectedKey, resultJson, ttlSeconds, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `get should retrieve result using correct key prefix`() {
        // Given
        val paymentOrderId = PaymentOrderId(456L)
        val expectedKey = "psp_result:456"
        val cachedResult = """{"status":"FAILED","errorCode":"INSUFFICIENT_FUNDS"}"""
        every { valueOperations.get(expectedKey) } returns cachedResult

        // When
        val result = adapter.get(paymentOrderId)

        // Then
        assertEquals(cachedResult, result)
        verify(exactly = 1) { valueOperations.get(expectedKey) }
    }

    @Test
    fun `get should return null when key does not exist`() {
        // Given
        val paymentOrderId = PaymentOrderId(789L)
        val expectedKey = "psp_result:789"
        every { valueOperations.get(expectedKey) } returns null

        // When
        val result = adapter.get(paymentOrderId)

        // Then
        assertNull(result)
        verify(exactly = 1) { valueOperations.get(expectedKey) }
    }

    @Test
    fun `remove should delete key using correct prefix`() {
        // Given
        val paymentOrderId = PaymentOrderId(999L)
        val expectedKey = "psp_result:999"
        every { redisTemplate.delete(expectedKey) } returns true

        // When
        adapter.remove(paymentOrderId)

        // Then
        verify(exactly = 1) { redisTemplate.delete(expectedKey) }
    }

    @Test
    fun `put should handle empty JSON string`() {
        // Given
        val paymentOrderId = PaymentOrderId(111L)
        val emptyJson = ""
        val expectedKey = "psp_result:111"

        // When
        adapter.put(paymentOrderId, emptyJson)

        // Then
        verify(exactly = 1) {
            valueOperations.set(expectedKey, emptyJson, ttlSeconds, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `put should handle large JSON payload`() {
        // Given
        val paymentOrderId = PaymentOrderId(222L)
        val largeJson = """{"data":"${"x".repeat(10000)}"}"""
        val expectedKey = "psp_result:222"

        // When
        adapter.put(paymentOrderId, largeJson)

        // Then
        verify(exactly = 1) {
            valueOperations.set(expectedKey, largeJson, ttlSeconds, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `put should handle special characters in JSON`() {
        // Given
        val paymentOrderId = PaymentOrderId(333L)
        val jsonWithSpecialChars = """{"message":"Test with \"quotes\" and \n newlines"}"""
        val expectedKey = "psp_result:333"

        // When
        adapter.put(paymentOrderId, jsonWithSpecialChars)

        // Then
        verify(exactly = 1) {
            valueOperations.set(expectedKey, jsonWithSpecialChars, ttlSeconds, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `multiple put operations should use same TTL`() {
        // Given
        val id1 = PaymentOrderId(100L)
        val id2 = PaymentOrderId(200L)
        val json1 = """{"status":"SUCCESS"}"""
        val json2 = """{"status":"FAILED"}"""

        // When
        adapter.put(id1, json1)
        adapter.put(id2, json2)

        // Then
        verify(exactly = 2) {
            valueOperations.set(any(), any(), ttlSeconds, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `adapter should use custom TTL from configuration`() {
        // Given
        val customTtl = 7200L
        val customAdapter = PspResultRedisCacheAdapter(redisTemplate, customTtl)
        val paymentOrderId = PaymentOrderId(444L)
        val resultJson = """{"status":"PENDING"}"""
        val expectedKey = "psp_result:444"

        // When
        customAdapter.put(paymentOrderId, resultJson)

        // Then
        verify(exactly = 1) {
            valueOperations.set(expectedKey, resultJson, customTtl, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `get should handle consecutive calls for same key`() {
        // Given
        val paymentOrderId = PaymentOrderId(555L)
        val expectedKey = "psp_result:555"
        val cachedResult = """{"status":"SUCCESS"}"""
        every { valueOperations.get(expectedKey) } returns cachedResult

        // When
        val result1 = adapter.get(paymentOrderId)
        val result2 = adapter.get(paymentOrderId)

        // Then
        assertEquals(cachedResult, result1)
        assertEquals(cachedResult, result2)
        verify(exactly = 2) { valueOperations.get(expectedKey) }
    }
}

