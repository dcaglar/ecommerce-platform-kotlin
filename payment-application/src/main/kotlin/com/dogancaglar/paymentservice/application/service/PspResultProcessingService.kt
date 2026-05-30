package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.time.Utc
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptured
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefunded
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.application.util.LedgerDomainEventEntityMapper
import com.dogancaglar.paymentservice.domain.util.LedgerEntryFactory
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.domain.model.ledger.LedgerOperationResult
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import org.slf4j.LoggerFactory
import java.util.UUID

open class PspResultProcessingService(
    private val ledgerWritePort: LedgerEntryPort,
    private val eventPublisherPort: EventPublisherPort,
    private val accountDirectory: AccountDirectoryPort,
    private val paymentTxPort: PaymentTxPort,
    private val idGeneratorPort: IdGeneratorPort
) {

    private val ledgerEntryFactory = LedgerEntryFactory()
    private val logger = LoggerFactory.getLogger(javaClass)

    fun processAuthorized(event: PaymentAuthorized) {
        val amount = Amount.of(event.totalAmountValue, Currency(event.currency))
        val authReceivable = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        )
        val authLiability = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.AUTH_LIABILITY, "GLOBAL")
        )
        
        val result = JournalEntry.authHold(
            txId = event.paymentId.toLongOrNull() ?: 0L,
            paymentId = event.paymentId.toLongOrNull() ?: 0L,
            acquirerReference = "REF-${event.paymentId}",
            journalIdentifier = event.paymentId,
            authorizedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability
        )
        
        saveOnly(result)
    }

    fun processCaptured(event: PaymentOrderCaptured) {
        val amount = Amount.of(event.amountValue, Currency(event.currency))
        val merchantAccount = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, event.sellerId)
        )
        val authReceivable = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        )
        val authLiability = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.AUTH_LIABILITY, "GLOBAL")
        )
        val pspReceivable = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.PSP_RECEIVABLES, "GLOBAL")
        )

        val txId = idGeneratorPort.nextPaymentId()
        val paymentId = event.paymentId.toLongOrNull() ?: 0L
        val paymentOrderId = event.paymentOrderId.toLongOrNull() ?: 0L
        val txs = paymentTxPort.findByPaymentId(paymentId)
        val authTx = txs.find { it.txType == "AUTHORIZATION" }
        val authTxId = authTx?.txId ?: 0L
        
        val captureResult = JournalEntry.capture(
            txId = txId,
            paymentId = paymentId,
            paymentOrderId = paymentOrderId,
            authorizationTxId = authTxId,
            acquirerReference = "REF-${paymentOrderId}",
            journalIdentifier = event.paymentOrderId,
            capturedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability,
            merchantAccount = merchantAccount,
            pspReceivable = pspReceivable
        )
        
        saveAndPublish(captureResult, event.paymentOrderId, event)
    }

    fun processRefunded(event: PaymentOrderRefunded) {
        val amount = Amount.of(event.amountValue, Currency(event.currency))
        val merchantAccount = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, event.sellerId)
        )
        val pspReceivable = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.PSP_RECEIVABLES, "GLOBAL")
        )
        val authReceivable = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        )
        val authLiability = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.AUTH_LIABILITY, "GLOBAL")
        )

        val txId = idGeneratorPort.nextPaymentId()
        val paymentId = event.paymentId.toLongOrNull() ?: 0L
        val paymentOrderId = event.paymentOrderId.toLongOrNull() ?: 0L
        val txs = paymentTxPort.findByPaymentId(paymentId)
        val captureTx = txs.find { it.txType == "CAPTURE" && it.paymentOrderId == paymentOrderId }
        val captureTxId = captureTx?.txId ?: 0L
        
        val refundResult = JournalEntry.refund(
            txId = txId,
            paymentId = paymentId,
            paymentOrderId = paymentOrderId,
            captureTxId = captureTxId,
            acquirerReference = "REF-${paymentOrderId}",
            journalIdentifier = event.paymentOrderId,
            refundedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability,
            merchantAccount = merchantAccount,
            pspReceivable = pspReceivable
        )
        
        saveAndPublish(refundResult, event.paymentOrderId, event)
    }

    private fun saveOnly(result: LedgerOperationResult) {
        if (result.journalEntries.isEmpty()) return
        paymentTxPort.save(result.transaction)
        val ledgerEntries = result.journalEntries.map { ledgerEntryFactory.create(it) }
        val persistedLedgerEntries = ledgerWritePort.postLedgerEntriesAtomic(ledgerEntries)
        if (persistedLedgerEntries.isEmpty()) {
            logger.warn("⚠️ No ledger entries were persisted (duplicate or error)")
        }
    }

    private fun saveAndPublish(
        result: LedgerOperationResult,
        aggregateIdentifier: String,
        sourceEvent: PaymentOrderEvent
    ) {
        if (result.journalEntries.isEmpty()) return

        // Persist the transaction first
        paymentTxPort.save(result.transaction)

        // 1️⃣ Transform journals to LedgerEntry
        val ledgerEntries = result.journalEntries.map { ledgerEntryFactory.create(it) }

        // 2️⃣ Persist ledger entries and get populated LedgerEntry objects with IDs
        val persistedLedgerEntries = ledgerWritePort.postLedgerEntriesAtomic(ledgerEntries)
        if (persistedLedgerEntries.isEmpty()) {
            logger.warn("⚠️ No ledger entries were persisted (duplicate or error)")
            return
        }

        // 3️⃣ Map LedgerEntry to LedgerEntryEventData for event publication
        val ledgerEntryEventDataList = persistedLedgerEntries.map { 
            LedgerDomainEventEntityMapper.toLedgerEntryEventData(it)
        }

        val recordedEvent = LedgerEntriesRecorded.from(sourceEvent, "ledger-batch-${UUID.randomUUID()}", ledgerEntryEventDataList, Utc.nowInstant())

        eventPublisherPort.publishSync(
            aggregateId = EventLogContext.getAggregateId()!!,
            data = recordedEvent,
            parentEventId = EventLogContext.getEventId(),
            traceId = EventLogContext.getTraceId()
        )
    }
}
