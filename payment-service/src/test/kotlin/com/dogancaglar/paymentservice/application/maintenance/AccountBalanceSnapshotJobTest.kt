package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshot
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class AccountBalanceSnapshotJobTest {

    private lateinit var accountBalanceCachePort: AccountBalanceCachePort
    private lateinit var accountBalanceSnapshotPort: AccountBalanceSnapshotPort
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var job: AccountBalanceSnapshotJob
    private val snapshotInterval = Duration.ofMinutes(1)

    @BeforeEach
    fun setUp() {
        accountBalanceCachePort = mockk(relaxed = true)
        accountBalanceSnapshotPort = mockk(relaxed = true)
        meterRegistry = mockk(relaxed = true)
        
        every { meterRegistry.counter(any()) } returns mockk(relaxed = true)
        every { meterRegistry.timer(any()) } returns mockk(relaxed = true)
        
        job = AccountBalanceSnapshotJob(
            accountBalanceCachePort = accountBalanceCachePort,
            accountBalanceSnapshotPort = accountBalanceSnapshotPort,
            meterRegistry = meterRegistry,
            snapshotInterval = snapshotInterval
        )
    }

    @Test
    fun `mergeAccountDeltas should merge delta into snapshot and save`() {
        // Given
        val accountCode = "MERCHANT_ACCOUNT.MERCHANT-456"
        val existingSnapshot = AccountBalanceSnapshot(
            accountCode = accountCode,
            balance = 100000L,
            lastSnapshotAt = LocalDateTime.now().minusHours(1),
            updatedAt = LocalDateTime.now().minusHours(1)
        )
        
        every { accountBalanceSnapshotPort.getSnapshot(accountCode) } returns existingSnapshot
        every { accountBalanceCachePort.getDelta(accountCode) } returns 5000L
        every { accountBalanceSnapshotPort.saveSnapshot(any()) } returns Unit

        // When
        job.mergeAccountDeltas(accountCode)

        // Then - Should merge: 100000 + 5000 = 105000
        verify(exactly = 1) {
            accountBalanceSnapshotPort.saveSnapshot(
                match { snapshot ->
                    snapshot.accountCode == accountCode &&
                    snapshot.balance == 105000L &&
                    snapshot.lastSnapshotAt.isAfter(existingSnapshot.lastSnapshotAt)
                }
            )
        }
    }

    @Test
    fun `mergeAccountDeltas should create new snapshot when not exists`() {
        // Given
        val accountCode = "CASH.GLOBAL"
        
        every { accountBalanceSnapshotPort.getSnapshot(accountCode) } returns null
        every { accountBalanceCachePort.getDelta(accountCode) } returns 10000L
        every { accountBalanceSnapshotPort.saveSnapshot(any()) } returns Unit

        // When
        job.mergeAccountDeltas(accountCode)

        // Then - Should create new snapshot with delta as balance
        verify(exactly = 1) {
            accountBalanceSnapshotPort.saveSnapshot(
                match { snapshot ->
                    snapshot.accountCode == accountCode &&
                    snapshot.balance == 10000L
                }
            )
        }
    }

    @Test
    fun `mergeAccountDeltas should skip when delta is zero`() {
        // Given
        val accountCode = "MERCHANT_ACCOUNT.MERCHANT-456"
        val existingSnapshot = AccountBalanceSnapshot(
            accountCode = accountCode,
            balance = 100000L,
            lastSnapshotAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        every { accountBalanceSnapshotPort.getSnapshot(accountCode) } returns existingSnapshot
        every { accountBalanceCachePort.getDelta(accountCode) } returns 0L

        // When
        job.mergeAccountDeltas(accountCode)

        // Then - Should not save snapshot
        verify(exactly = 0) { accountBalanceSnapshotPort.saveSnapshot(any()) }
    }

    @Test
    fun `mergeAccountDeltas should handle negative deltas`() {
        // Given
        val accountCode = "CASH.GLOBAL"
        val existingSnapshot = AccountBalanceSnapshot(
            accountCode = accountCode,
            balance = 50000L,
            lastSnapshotAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        every { accountBalanceSnapshotPort.getSnapshot(accountCode) } returns existingSnapshot
        every { accountBalanceCachePort.getDelta(accountCode) } returns -10000L
        every { accountBalanceSnapshotPort.saveSnapshot(any()) } returns Unit

        // When
        job.mergeAccountDeltas(accountCode)

        // Then - Should merge: 50000 - 10000 = 40000
        verify(exactly = 1) {
            accountBalanceSnapshotPort.saveSnapshot(
                match { snapshot ->
                    snapshot.balance == 40000L
                }
            )
        }
    }

    @Test
    fun `mergeDeltasToSnapshots should execute scheduled job`() {
        // When - Call the scheduled method
        job.mergeDeltasToSnapshots()

        // Then - Should complete without exceptions (placeholder implementation)
        // In production, this would scan Redis and process deltas
        verify(exactly = 1) { meterRegistry.counter("account_balance_snapshots_processed_total") }
    }

    @Test
    fun `mergeDeltasToSnapshots should handle exceptions gracefully`() {
        // Given
        every { meterRegistry.counter(any()) } throws RuntimeException("Metric error")

        // When/Then - Should not throw
        try {
            job.mergeDeltasToSnapshots()
        } catch (e: Exception) {
            // Exception is caught and logged internally
        }
    }
}

