package com.dogancaglar.paymentservice.adapter.outbound.redis

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

class BalanceIdempotencyAdapterTest {

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOperations: ValueOperations<String, String>
    private lateinit var adapter: BalanceIdempotencyAdapter
    private val idempotencyTtlSeconds = 86400L

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk(relaxed = true)
        valueOperations = mockk(relaxed = true)
        every { redisTemplate.opsForValue() } returns valueOperations
        adapter = BalanceIdempotencyAdapter(redisTemplate, idempotencyTtlSeconds)
    }

    @Test
    fun `areLedgerEntryIdsProcessed should return false when no IDs are processed`() {
        // Given
        val ledgerEntryIds = listOf(1001L, 1002L, 1003L)
        every { redisTemplate.hasKey("balance:processed:1001") } returns false
        every { redisTemplate.hasKey("balance:processed:1002") } returns false
        every { redisTemplate.hasKey("balance:processed:1003") } returns false

        // When
        val result = adapter.areLedgerEntryIdsProcessed(ledgerEntryIds)

        // Then - Should check all keys since none are found (early exit only on first match)
        assertFalse(result)
        verify(exactly = 1) { redisTemplate.hasKey("balance:processed:1001") }
        verify(exactly = 1) { redisTemplate.hasKey("balance:processed:1002") }
        verify(exactly = 1) { redisTemplate.hasKey("balance:processed:1003") }
    }

    @Test
    fun `areLedgerEntryIdsProcessed should return true when any ID is processed`() {
        // Given
        val ledgerEntryIds = listOf(1001L, 1002L, 1003L)
        every { redisTemplate.hasKey("balance:processed:1001") } returns false
        every { redisTemplate.hasKey("balance:processed:1002") } returns true  // Found processed ID

        // When
        val result = adapter.areLedgerEntryIdsProcessed(ledgerEntryIds)

        // Then
        assertTrue(result)
        verify(exactly = 1) { redisTemplate.hasKey("balance:processed:1001") }
        verify(exactly = 1) { redisTemplate.hasKey("balance:processed:1002") }
        verify(exactly = 0) { redisTemplate.hasKey("balance:processed:1003") } // Early exit on first true
    }

    @Test
    fun `areLedgerEntryIdsProcessed should return false for empty list`() {
        // When
        val result = adapter.areLedgerEntryIdsProcessed(emptyList())

        // Then
        assertFalse(result)
        verify(exactly = 0) { redisTemplate.hasKey(any()) }
    }

    @Test
    fun `markLedgerEntryIdsProcessed should set keys with TTL`() {
        // Given
        val ledgerEntryIds = listOf(1001L, 1002L, 1003L)
        every { valueOperations.setIfAbsent(any(), any(), any(), any()) } returns true

        // When
        adapter.markLedgerEntryIdsProcessed(ledgerEntryIds)

        // Then - Each ID should be marked with TTL
        verify(exactly = 1) {
            valueOperations.setIfAbsent(
                "balance:processed:1001",
                "1",
                idempotencyTtlSeconds,
                TimeUnit.SECONDS
            )
        }
        verify(exactly = 1) {
            valueOperations.setIfAbsent(
                "balance:processed:1002",
                "1",
                idempotencyTtlSeconds,
                TimeUnit.SECONDS
            )
        }
        verify(exactly = 1) {
            valueOperations.setIfAbsent(
                "balance:processed:1003",
                "1",
                idempotencyTtlSeconds,
                TimeUnit.SECONDS
            )
        }
    }

    @Test
    fun `markLedgerEntryIdsProcessed should skip empty list`() {
        // When
        adapter.markLedgerEntryIdsProcessed(emptyList())

        // Then
        verify(exactly = 0) { valueOperations.setIfAbsent(any(), any(), any(), any()) }
    }

    @Test
    fun `markLedgerEntryIdsProcessed should be idempotent`() {
        // Given - Setting same key twice should be safe
        val ledgerEntryIds = listOf(1001L)
        every { valueOperations.setIfAbsent(any(), any(), any(), any()) } returns false  // Already exists

        // When
        adapter.markLedgerEntryIdsProcessed(ledgerEntryIds)

        // Then - Should still call setIfAbsent (idempotent operation)
        verify(exactly = 1) {
            valueOperations.setIfAbsent(
                "balance:processed:1001",
                "1",
                idempotencyTtlSeconds,
                TimeUnit.SECONDS
            )
        }
    }
}

