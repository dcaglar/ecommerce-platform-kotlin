package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.paymentservice.domain.model.balance.AccountBalanceSnapshot
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.Duration

@Service
class AccountBalanceSnapshotJob(
    private val cachePort: AccountBalanceCachePort,
    private val snapshotPort: AccountBalanceSnapshotPort,
    @Value("\${account-balance.snapshot-interval:PT1M}")
    private val snapshotInterval: Duration
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${account-balance.snapshot-interval:PT1M}")
    fun mergeDeltasToSnapshots() {
        logger.info("🔁 Starting AccountBalanceSnapshotJob")

        try {
            val dirtyAccounts = cachePort.getDirtyAccounts()
            if (dirtyAccounts.isEmpty()) {
                logger.debug("No dirty accounts to merge")
                return
            }

            dirtyAccounts.forEach { accountCode ->
                val (delta, upToEntryId) = cachePort.getAndResetDeltaWithWatermark(accountCode)
                if (delta == 0L) return@forEach

                val current = snapshotPort.getSnapshot(accountCode)
                    ?: AccountBalanceSnapshot(accountCode, 0L, 0L, LocalDateTime.now(), LocalDateTime.now())

                val newBalance = current.balance + delta
                val newWatermark = maxOf(current.lastAppliedEntryId, upToEntryId)

                val updated = current.copy(
                    balance = newBalance,
                    lastAppliedEntryId = newWatermark,
                    lastSnapshotAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )

                snapshotPort.saveSnapshot(updated)
                logger.info("✅ Merged Δ{} for {}, new balance={}, watermark={}", delta, accountCode, newBalance, newWatermark)
            }

        } catch (ex: Exception) {
            logger.error("❌ Error during snapshot merge job", ex)
        }
    }
}