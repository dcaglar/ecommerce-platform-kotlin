package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.AccountBalanceEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.AccountBalanceMapper
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshot
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AccountBalanceSnapshotAdapterTest {

    private lateinit var accountBalanceMapper: AccountBalanceMapper
    private lateinit var adapter: AccountBalanceSnapshotAdapter

    @BeforeEach
    fun setUp() {
        accountBalanceMapper = mockk(relaxed = true)
        adapter = AccountBalanceSnapshotAdapter(accountBalanceMapper)
    }

    @Test
    fun `getSnapshot should return snapshot when exists`() {
        // Given
        val accountCode = "MERCHANT_ACCOUNT.MERCHANT-456"
        val entity = AccountBalanceEntity(
            accountCode = accountCode,
            balance = 100000L,
            lastSnapshotAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        every { accountBalanceMapper.findByAccountCode(accountCode) } returns entity

        // When
        val result = adapter.getSnapshot(accountCode)

        // Then
        assertNotNull(result)
        assertEquals(accountCode, result?.accountCode)
        assertEquals(100000L, result?.balance)
        verify(exactly = 1) { accountBalanceMapper.findByAccountCode(accountCode) }
    }

    @Test
    fun `getSnapshot should return null when not exists`() {
        // Given
        val accountCode = "NONEXISTENT.ACCOUNT"
        every { accountBalanceMapper.findByAccountCode(accountCode) } returns null

        // When
        val result = adapter.getSnapshot(accountCode)

        // Then
        assertNull(result)
        verify(exactly = 1) { accountBalanceMapper.findByAccountCode(accountCode) }
    }

    @Test
    fun `saveSnapshot should insert new snapshot`() {
        // Given
        val snapshot = AccountBalanceSnapshot(
            accountCode = "MERCHANT_ACCOUNT.MERCHANT-456",
            balance = 150000L,
            lastSnapshotAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        every { accountBalanceMapper.insertOrUpdateSnapshot(any()) } returns 1

        // When
        adapter.saveSnapshot(snapshot)

        // Then
        verify(exactly = 1) {
            accountBalanceMapper.insertOrUpdateSnapshot(
                match { entity ->
                    entity.accountCode == snapshot.accountCode &&
                    entity.balance == snapshot.balance
                }
            )
        }
    }

    @Test
    fun `saveSnapshot should update existing snapshot`() {
        // Given
        val snapshot = AccountBalanceSnapshot(
            accountCode = "CASH.GLOBAL",
            balance = 75000L,
            lastSnapshotAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        every { accountBalanceMapper.insertOrUpdateSnapshot(any()) } returns 1

        // When
        adapter.saveSnapshot(snapshot)

        // Then - Should use insertOrUpdate (ON CONFLICT DO UPDATE)
        verify(exactly = 1) {
            accountBalanceMapper.insertOrUpdateSnapshot(
                match { entity ->
                    entity.accountCode == snapshot.accountCode &&
                    entity.balance == snapshot.balance
                }
            )
        }
    }

    @Test
    fun `findAllSnapshots should return list of snapshots`() {
        // Given
        val entities = listOf(
            AccountBalanceEntity(
                accountCode = "MERCHANT_ACCOUNT.MERCHANT-456",
                balance = 100000L,
                lastSnapshotAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            ),
            AccountBalanceEntity(
                accountCode = "CASH.GLOBAL",
                balance = 50000L,
                lastSnapshotAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
        
        every { accountBalanceMapper.findAll() } returns entities

        // When
        val result = adapter.findAllSnapshots()

        // Then
        assertEquals(2, result.size)
        assertEquals(100000L, result[0].balance)
        assertEquals(50000L, result[1].balance)
        verify(exactly = 1) { accountBalanceMapper.findAll() }
    }

    @Test
    fun `findAllSnapshots should return empty list when no snapshots exist`() {
        // Given
        every { accountBalanceMapper.findAll() } returns emptyList()

        // When
        val result = adapter.findAllSnapshots()

        // Then
        assertEquals(0, result.size)
        verify(exactly = 1) { accountBalanceMapper.findAll() }
    }
}

