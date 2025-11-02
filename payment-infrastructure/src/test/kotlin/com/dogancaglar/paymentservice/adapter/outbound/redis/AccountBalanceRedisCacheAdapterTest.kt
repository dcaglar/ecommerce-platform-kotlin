package com.dogancaglar.paymentservice.adapter.outbound.redis

import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

class AccountBalanceRedisCacheAdapterTest {

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOperations: ValueOperations<String, String>
    private lateinit var adapter: AccountBalanceRedisCacheAdapter
    private val deltaTtlSeconds = 300L

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk(relaxed = true)
        valueOperations = mockk(relaxed = true)
        every { redisTemplate.opsForValue() } returns valueOperations
        adapter = AccountBalanceRedisCacheAdapter(redisTemplate, deltaTtlSeconds)
    }

    @Test
    fun `incrementDelta should increment value and set TTL`() {
        // Given
        val accountCode = "MERCHANT_ACCOUNT.MERCHANT-456"
        val delta = 10000L
        val key = "balance:delta:$accountCode"
        
        every { valueOperations.increment(key, delta) } returns 15000L

        // When
        adapter.incrementDelta(accountCode, delta)

        // Then
        verify(exactly = 1) { valueOperations.increment(key, delta) }
        verify(exactly = 1) { redisTemplate.expire(key, deltaTtlSeconds, TimeUnit.SECONDS) }
    }

    @Test
    fun `incrementDelta should handle negative deltas`() {
        // Given
        val accountCode = "CASH.GLOBAL"
        val delta = -5000L
        val key = "balance:delta:$accountCode"
        
        every { valueOperations.increment(key, delta) } returns 5000L

        // When
        adapter.incrementDelta(accountCode, delta)

        // Then
        verify(exactly = 1) { valueOperations.increment(key, delta) }
        verify(exactly = 1) { redisTemplate.expire(key, deltaTtlSeconds, TimeUnit.SECONDS) }
    }

    @Test
    fun `getDelta should return 0 when key does not exist`() {
        // Given
        val accountCode = "MERCHANT_ACCOUNT.MERCHANT-456"
        val key = "balance:delta:$accountCode"
        
        every { valueOperations.get(key) } returns null

        // When
        val result = adapter.getDelta(accountCode)

        // Then
        assertEquals(0L, result)
        verify(exactly = 1) { valueOperations.get(key) }
    }

    @Test
    fun `getDelta should return existing value`() {
        // Given
        val accountCode = "MERCHANT_ACCOUNT.MERCHANT-456"
        val key = "balance:delta:$accountCode"
        
        every { valueOperations.get(key) } returns "25000"

        // When
        val result = adapter.getDelta(accountCode)

        // Then
        assertEquals(25000L, result)
        verify(exactly = 1) { valueOperations.get(key) }
    }

    @Test
    fun `getRealTimeBalance should return snapshot plus delta`() {
        // Given
        val accountCode = "MERCHANT_ACCOUNT.MERCHANT-456"
        val key = "balance:delta:$accountCode"
        val snapshotBalance = 100000L
        
        every { valueOperations.get(key) } returns "5000"  // Delta = 5000

        // When
        val result = adapter.getRealTimeBalance(accountCode, snapshotBalance)

        // Then
        assertEquals(105000L, result)  // 100000 + 5000
        verify(exactly = 1) { valueOperations.get(key) }
    }

    @Test
    fun `getRealTimeBalance should return snapshot when delta is 0`() {
        // Given
        val accountCode = "MERCHANT_ACCOUNT.MERCHANT-456"
        val snapshotBalance = 100000L
        
        every { valueOperations.get("balance:delta:$accountCode") } returns null  // No delta

        // When
        val result = adapter.getRealTimeBalance(accountCode, snapshotBalance)

        // Then
        assertEquals(100000L, result)  // snapshot + 0
    }

    @Test
    fun `getRealTimeBalance should handle negative deltas`() {
        // Given
        val accountCode = "CASH.GLOBAL"
        val snapshotBalance = 50000L
        
        every { valueOperations.get("balance:delta:$accountCode") } returns "-10000"  // Negative delta

        // When
        val result = adapter.getRealTimeBalance(accountCode, snapshotBalance)

        // Then
        assertEquals(40000L, result)  // 50000 - 10000
    }
}

