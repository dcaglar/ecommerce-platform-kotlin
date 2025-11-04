package com.dogancaglar.paymentservice.domain.event

import java.time.LocalDateTime

/**
 * Domain event published when ledger entries have been recorded.
 * 
 * This event is only created through the factory method to ensure invariants are maintained.
 */
data class LedgerEntriesRecorded private constructor(
    override val ledgerBatchId: String,
    override val paymentOrderId: String,
    override val publicPaymentOrderId: String,
    override val sellerId: String,
    override val currency: String,
    override val status: String,
    override val recordedAt: LocalDateTime,
    val entryCount: Int,
    val ledgerEntries: List<LedgerEntryEventData> = emptyList(),
    override val traceId: String? = null,
    override val parentEventId: String? = null
) : LedgerEvent {
    
    companion object {
        /**
         * Factory method to create LedgerEntriesRecorded event.
         * 
         * Ensures entryCount matches the actual size of ledgerEntries list.
         * 
         * @param ledgerBatchId Batch ID for this ledger recording batch
         * @param paymentOrderId Internal payment order ID
         * @param publicPaymentOrderId Public payment order ID
         * @param sellerId Seller/merchant ID
         * @param currency Currency code
         * @param status Payment status
         * @param recordedAt Timestamp when ledger entries were recorded
         * @param ledgerEntries List of ledger entry event data
         * @param traceId Optional trace ID for distributed tracing
         * @param parentEventId Optional parent event ID
         * @return LedgerEntriesRecorded event with validated entryCount
         */
        fun create(
            ledgerBatchId: String,
            paymentOrderId: String,
            publicPaymentOrderId: String,
            sellerId: String,
            currency: String,
            status: String,
            recordedAt: LocalDateTime,
            ledgerEntries: List<LedgerEntryEventData>,
            traceId: String? = null,
            parentEventId: String? = null
        ): LedgerEntriesRecorded {
            // Validate that entryCount matches actual size
            val entryCount = ledgerEntries.size
            
            return LedgerEntriesRecorded(
                ledgerBatchId = ledgerBatchId,
                paymentOrderId = paymentOrderId,
                publicPaymentOrderId = publicPaymentOrderId,
                sellerId = sellerId,
                currency = currency,
                status = status,
                recordedAt = recordedAt,
                entryCount = entryCount,
                ledgerEntries = ledgerEntries,
                traceId = traceId,
                parentEventId = parentEventId
            )
        }
    }
}