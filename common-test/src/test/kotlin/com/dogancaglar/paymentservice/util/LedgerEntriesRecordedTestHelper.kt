package com.dogancaglar.paymentservice.util

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.domain.event.LedgerEntryEventData
import com.dogancaglar.paymentservice.domain.event.PostingDirection
import com.dogancaglar.paymentservice.domain.event.PostingEventData
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType

/**
 * Test helper to generate LedgerEntriesRecorded events and related test data.
 * 
 * Provides helpers for:
 * - Full authHoldAndCapture transaction events (complex, production-like)
 * - Simple test events with custom postings (for consumer/integration tests)
 * - Individual LedgerEntryEventData builders
 */
object LedgerEntriesRecordedTestHelper {
    
    /**
     * Creates a simple LedgerEntryEventData for testing.
     * 
     * @param ledgerEntryId Ledger entry ID
     * @param journalEntryId Journal entry ID (e.g., "CAPTURE:paymentorder-123")
     * @param journalType Journal type (e.g., JournalType.CAPTURE)
     * @param journalName Journal name (e.g., "Payment Capture")
     * @param createdAt Timestamp when ledger entry was created
     * @param postings List of postings for this ledger entry
     * @return LedgerEntryEventData with specified values
     */
    fun createLedgerEntryEventData(
        ledgerEntryId: Long,
        journalEntryId: String,
        journalType: JournalType,
        journalName: String,
        createdAt: java.time.LocalDateTime,
        postings: List<PostingEventData>
    ): LedgerEntryEventData {
        return LedgerEntryEventData.create(
            ledgerEntryId = ledgerEntryId,
            journalEntryId = journalEntryId,
            journalType = journalType,
            journalName = journalName,
            createdAt = createdAt,
            postings = postings
        )
    }
    
    /**
     * Creates a simple LedgerEntryEventData with a single merchant account posting.
     * Useful for consumer/integration tests that don't need full authHoldAndCapture structure.
     * 
     * @param ledgerEntryId Ledger entry ID
     * @param sellerId Seller/merchant ID (used in merchant account code)
     * @param currency Currency code (e.g., "USD", "EUR")
     * @param amount Amount in minor currency units (default: 10000L)
     * @param direction Posting direction (default: CREDIT for merchant account)
     * @return LedgerEntryEventData with single MERCHANT_PAYABLE posting
     */
    fun createSimpleLedgerEntryEventData(
        ledgerEntryId: Long,
        sellerId: String,
        currency: String,
        amount: Long = 10000L,
        direction: PostingDirection = PostingDirection.CREDIT
    ): LedgerEntryEventData {
        return createLedgerEntryEventData(
            ledgerEntryId = ledgerEntryId,
            journalEntryId = "JOURNAL:$ledgerEntryId",
            journalType = JournalType.CAPTURE,
            journalName = "Test Journal",
            createdAt = java.time.LocalDateTime.now(),
            postings = listOf(
                PostingEventData.create(
                    accountCode = "MERCHANT_PAYABLE.$sellerId",
                    accountType = AccountType.MERCHANT_PAYABLE,
                    amount = amount,
                    currency = currency,
                    direction = direction
                )
            )
        )
    }
    
    /**
     * Creates a simple LedgerEntriesRecorded event for testing.
     * Useful for consumer/integration tests.
     * 
     * @param paymentOrderId Internal payment order ID
     * @param publicPaymentOrderId Public payment order ID
     * @param sellerId Seller/merchant ID
     * @param currency Currency code
     * @param status Payment status (default: "SUCCESSFUL_FINAL")
     * @param recordedAt Timestamp when ledger entries were recorded
     * @param ledgerEntries List of ledger entry event data
     * @param ledgerBatchId Batch ID (auto-generated if null)
     * @param traceId Optional trace ID
     * @param parentEventId Optional parent event ID
     * @return LedgerEntriesRecorded event with specified values
     */
    fun createLedgerEntriesRecorded(
        paymentOrderId: String,
        publicPaymentOrderId: String,
        sellerId: String,
        currency: String,
        recordedAt: java.time.LocalDateTime,
        ledgerEntries: List<LedgerEntryEventData>,
        status: String = "SUCCESSFUL_FINAL",
        ledgerBatchId: String? = null,
        traceId: String? = null,
        parentEventId: String? = null
    ): LedgerEntriesRecorded {
        val batchId = ledgerBatchId ?: "batch-${java.util.UUID.randomUUID()}"
        
        return LedgerEntriesRecorded.create(
            ledgerBatchId = batchId,
            paymentOrderId = paymentOrderId,
            sellerId = sellerId,
            currency = currency,
            status = status,
            recordedAt = recordedAt,
            ledgerEntries = ledgerEntries,
            traceId = traceId,
            parentEventId = parentEventId
        )
    }
    
    /**
     * Generates the expected LedgerEntriesRecorded event for authHoldAndCapture transaction.
     * 
     * @param paymentOrderId Internal payment order ID
     * @param publicPaymentOrderId Public payment order ID (used in journal entry IDs)
     * @param sellerId Seller/merchant ID (used in merchant account code)
     * @param amount Amount in minor currency units (e.g., cents)
     * @param currency Currency code (e.g., "USD", "EUR")
     * @param authLedgerEntryId First ledger entry ID (for AUTH_HOLD journal entry)
     * @param captureLedgerEntryId Second ledger entry ID (for CAPTURE journal entry)
     * @param recordedAt Timestamp when ledger entries were recorded
     * @param authCreatedAt Timestamp when AUTH_HOLD ledger entry was created
     * @param captureCreatedAt Timestamp when CAPTURE ledger entry was created
     * @param status Payment status (typically "SUCCESSFUL_FINAL")
     * @param ledgerBatchId Batch ID (will be prefixed with "ledger-batch-" if not provided)
     * @param traceId Optional trace ID for distributed tracing
     * @param parentEventId Optional parent event ID
     * @return Expected LedgerEntriesRecorded event matching authHoldAndCapture structure
     */
    fun expectedAuthHoldAndCaptureEvent(
        paymentOrderId: String,
        sellerId: String,
        amount: Long,
        currency: String,
        authLedgerEntryId: Long,
        captureLedgerEntryId: Long,
        recordedAt: java.time.LocalDateTime,
        authCreatedAt: java.time.LocalDateTime,
        captureCreatedAt: java.time.LocalDateTime,
        status: String = "SUCCESSFUL_FINAL",
        ledgerBatchId: String? = null,
        traceId: String? = null,
        parentEventId: String? = null
    ): LedgerEntriesRecorded {
        val batchId = ledgerBatchId ?: "ledger-batch-${java.util.UUID.randomUUID()}"
        
        return LedgerEntriesRecorded.create(
            ledgerBatchId = batchId,
            paymentOrderId = paymentOrderId,
            sellerId = sellerId,
            currency = currency,
            status = status,
            recordedAt = recordedAt,
            ledgerEntries = listOf(
                // First LedgerEntryEventData: AUTH_HOLD
                LedgerEntryEventData.create(
                    ledgerEntryId = authLedgerEntryId,
                    journalEntryId = "AUTH:$paymentOrderId",
                    journalType = JournalType.AUTH_HOLD,
                    journalName = "Authorization Hold",
                    createdAt = authCreatedAt,
                    postings = listOf(
                        // AUTH_HOLD Posting 1: AUTH_RECEIVABLE.GLOBAL.{currency} DEBIT
                        PostingEventData.create(
                            accountCode = "AUTH_RECEIVABLE.GLOBAL.$currency",
                            accountType = AccountType.AUTH_RECEIVABLE,
                            amount = amount,
                            currency = currency,
                            direction = PostingDirection.DEBIT
                        ),
                        // AUTH_HOLD Posting 2: AUTH_LIABILITY.GLOBAL.{currency} CREDIT
                        PostingEventData.create(
                            accountCode = "AUTH_LIABILITY.GLOBAL.$currency",
                            accountType = AccountType.AUTH_LIABILITY,
                            amount = amount,
                            currency = currency,
                            direction = PostingDirection.CREDIT
                        )
                    )
                ),
                // Second LedgerEntryEventData: CAPTURE
                LedgerEntryEventData.create(
                    ledgerEntryId = captureLedgerEntryId,
                    journalEntryId = "CAPTURE:$paymentOrderId",
                    journalType = JournalType.CAPTURE,
                    journalName = "Payment Capture",
                    createdAt = captureCreatedAt,
                    postings = listOf(
                        // CAPTURE Posting 1: AUTH_RECEIVABLE.GLOBAL.{currency} CREDIT
                        PostingEventData.create(
                            accountCode = "AUTH_RECEIVABLE.GLOBAL.$currency",
                            accountType = AccountType.AUTH_RECEIVABLE,
                            amount = amount,
                            currency = currency,
                            direction = PostingDirection.CREDIT
                        ),
                        // CAPTURE Posting 2: AUTH_LIABILITY.GLOBAL.{currency} DEBIT
                        PostingEventData.create(
                            accountCode = "AUTH_LIABILITY.GLOBAL.$currency",
                            accountType = AccountType.AUTH_LIABILITY,
                            amount = amount,
                            currency = currency,
                            direction = PostingDirection.DEBIT
                        ),
                        // CAPTURE Posting 3: MERCHANT_PAYABLE.{sellerId}.{currency} CREDIT
                        PostingEventData.create(
                            accountCode = "MERCHANT_PAYABLE.$sellerId.$currency",
                            accountType = AccountType.MERCHANT_PAYABLE,
                            amount = amount,
                            currency = currency,
                            direction = PostingDirection.CREDIT
                        ),
                        // CAPTURE Posting 4: PSP_RECEIVABLES.GLOBAL.{currency} DEBIT
                        PostingEventData.create(
                            accountCode = "PSP_RECEIVABLES.GLOBAL.$currency",
                            accountType = AccountType.PSP_RECEIVABLES,
                            amount = amount,
                            currency = currency,
                            direction = PostingDirection.DEBIT
                        )
                    )
                )
            ),
            traceId = traceId,
            parentEventId = parentEventId
        )
    }

    fun wrapInEnvelope(event: LedgerEntriesRecorded): EventEnvelope<LedgerEntriesRecorded> {
        return DomainEventEnvelopeFactory.envelopeFor(
            data = event,
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = event.sellerId,
            traceId = "trace-123"
        )
    }
}