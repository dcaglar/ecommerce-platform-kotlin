package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshot
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.Duration

/**
 * Scheduled job that periodically merges Redis deltas into database snapshots.
 * 
 * Flow:
 * 1. Read current snapshots from DB (or default to 0)
 * 2. Read deltas from Redis
 * 3. Merge: snapshot = snapshot + delta
 * 4. Save merged snapshots to DB
 * 5. Deltas expire naturally via TTL (no explicit clearing needed)
 */
@Service
class AccountBalanceSnapshotJob(
    private val accountBalanceCachePort: AccountBalanceCachePort,
    private val accountBalanceSnapshotPort: AccountBalanceSnapshotPort,
    private val meterRegistry: MeterRegistry,
    @Value("\${account-balance.snapshot-interval:PT1M}")
    private val snapshotInterval: Duration
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    private val snapshotsProcessedCounter = meterRegistry.counter("account_balance_snapshots_processed_total")
    private val snapshotProcessingTimer = meterRegistry.timer("account_balance_snapshot_processing_seconds")
    
    @Scheduled(fixedDelayString = "\${account-balance.snapshot-interval:PT1M}")
    fun mergeDeltasToSnapshots() {
        val timerSample = Timer.start(meterRegistry)
        
        try {
            logger.debug("Starting periodic snapshot merge job")
            
            // Strategy: Scan Redis for delta keys and process them
            // Note: In production, you might want to track sellers with deltas separately
            // For now, we'll use a simple approach: process known accounts from DB
            // and any new accounts discovered from Redis deltas
            
            // TODO: Implement Redis SCAN to find all delta keys efficiently
            // For now, this is a placeholder - in practice you'd scan Redis keys with pattern "balance:delta:*"
            
            logger.debug("Snapshot merge job completed")
            snapshotsProcessedCounter.increment()
        } catch (ex: Exception) {
            logger.error("Error during snapshot merge job", ex)
        } finally {
            timerSample.stop(snapshotProcessingTimer)
        }
    }
    
    /**
     * Merges deltas for a specific account.
     * Can be called on-demand or from the scheduled job.
     */
    fun mergeAccountDeltas(accountCode: String) {
        val currentSnapshot = accountBalanceSnapshotPort.getSnapshot(accountCode)
            ?: AccountBalanceSnapshot(
                accountCode = accountCode,
                balance = 0L,
                lastSnapshotAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        
        val delta = accountBalanceCachePort.getDelta(accountCode)
        
        if (delta == 0L) {
            return // No delta to merge
        }
        
        val mergedBalance = currentSnapshot.balance + delta
        
        val mergedSnapshot = currentSnapshot.copy(
            balance = mergedBalance,
            lastSnapshotAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        accountBalanceSnapshotPort.saveSnapshot(mergedSnapshot)
        
        logger.debug(
            "Merged delta {} into snapshot for account {}, new balance: {}",
            delta,
            accountCode,
            mergedBalance
        )
    }
}

