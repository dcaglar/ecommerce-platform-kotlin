package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.event.LedgerEntryEventData
import com.dogancaglar.paymentservice.domain.event.PostingDirection
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.BalanceIdempotencyPort
import org.slf4j.LoggerFactory

/**
 * Service for updating account balances from ledger entries.
 * Implements idempotency checks and batch processing of balance deltas.
 */
class AccountBalanceService(
    private val balanceIdempotencyPort: BalanceIdempotencyPort,
    private val accountBalanceCachePort: AccountBalanceCachePort
) : AccountBalanceUseCase {
    
    private val logger = LoggerFactory.getLogger(javaClass)
    
    override fun updateAccountBalancesBatch(
        ledgerEntries: List<LedgerEntryEventData>
    ): List<Long> {
        if (ledgerEntries.isEmpty()) return emptyList()
        
        // Extract all ledger entry IDs for idempotency check
        val ledgerEntryIds = ledgerEntries.map { it.ledgerEntryId }
        
        // Check if any have already been processed
        if (balanceIdempotencyPort.areLedgerEntryIdsProcessed(ledgerEntryIds)) {
            logger.debug("Skipping already processed ledger entry IDs: {}", ledgerEntryIds)
            return emptyList() // All already processed
        }
        
        // Aggregate deltas per account across all ledger entries in batch
        val accountDeltas = mutableMapOf<String, Long>()
        
        ledgerEntries.forEach { ledgerEntry ->
            ledgerEntry.postings.forEach { posting ->
                // Calculate signed amount based on account type and direction
                val signedAmount = calculateSignedAmount(posting)
                
                // Use accountCode directly as account ID
                val accountCode = posting.accountCode
                
                // Aggregate delta
                accountDeltas[accountCode] = accountDeltas.getOrDefault(accountCode, 0L) + signedAmount
            }
        }
        
        // Batch update Redis deltas
        accountDeltas.forEach { (accountCode, delta) ->
            accountBalanceCachePort.incrementDelta(accountCode, delta)
        }
        
        logger.info(
            "Updated balance deltas for {} accounts from {} ledger entries",
            accountDeltas.size,
            ledgerEntries.size
        )
        
        // Mark ledger entry IDs as processed (before offset commit)
        balanceIdempotencyPort.markLedgerEntryIdsProcessed(ledgerEntryIds)
        
        return ledgerEntryIds
    }
    
    /**
     * Calculates the signed amount for a posting based on account type and direction.
     * 
     * For debit accounts (normalBalance=DEBIT): +amount for DEBIT, -amount for CREDIT
     * For credit accounts (normalBalance=CREDIT): -amount for DEBIT, +amount for CREDIT
     */
    private fun calculateSignedAmount(posting: com.dogancaglar.paymentservice.domain.event.PostingEventData): Long {
        val isDebitAccount = posting.accountType.normalBalance == 
            com.dogancaglar.paymentservice.domain.model.ledger.NormalBalance.DEBIT
        
        return when {
            isDebitAccount && posting.direction == PostingDirection.DEBIT -> posting.amount
            isDebitAccount && posting.direction == PostingDirection.CREDIT -> -posting.amount
            !isDebitAccount && posting.direction == PostingDirection.DEBIT -> -posting.amount
            !isDebitAccount && posting.direction == PostingDirection.CREDIT -> posting.amount
            else -> 0L
        }
    }
    
}

