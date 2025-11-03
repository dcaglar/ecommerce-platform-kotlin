package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.model.ledger.LedgerEntry
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import org.slf4j.LoggerFactory

/**
 * Service for updating account balances from ledger entries.
 * Consumes domain-level LedgerEntry objects (not event DTOs).
 * Uses each Posting's signed amount logic for correctness.
 */
class AccountBalanceService(
    private val snapshotPort: AccountBalanceSnapshotPort,
    private val cachePort: AccountBalanceCachePort
) : AccountBalanceUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun updateAccountBalancesBatch(ledgerEntries: List<LedgerEntry>): List<Long> {
        if (ledgerEntries.isEmpty()) return emptyList()

        val postingsByAccount = ledgerEntries
            .flatMap { entry ->
                entry.journalEntry.postings.map { posting ->
                    Triple(
                        posting.account.accountCode,
                        posting.getSignedAmount().quantity,
                        entry.ledgerEntryId
                    )
                }
            }
            .groupBy { it.first }

        val accountCodes = postingsByAccount.keys
        val currentSnapshots = snapshotPort.findByAccountCodes(accountCodes).associateBy { it.accountCode }

        val updatedIds = mutableSetOf<Long>()
        postingsByAccount.forEach { (accountCode, postings) ->
            val currentWatermark = currentSnapshots[accountCode]?.lastAppliedEntryId ?: 0L
            val newPostings = postings.filter { (_, _, id) -> id > currentWatermark }
            if (newPostings.isEmpty()) return@forEach

            val delta = newPostings.sumOf { it.second } // signed amounts already correct
            val maxId = newPostings.maxOf { it.third }

            if (delta != 0L) {
                cachePort.addDeltaAndWatermark(accountCode, delta, maxId)
                cachePort.markDirty(accountCode)
                updatedIds += maxId
            }
        }

        logger.info("✅ Updated {} accounts with new deltas ({} ledger entries total)",
            updatedIds.size, ledgerEntries.size)
        return updatedIds.toList()
    }
}