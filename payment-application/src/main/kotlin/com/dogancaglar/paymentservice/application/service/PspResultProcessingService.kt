package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.time.Utc
import com.dogancaglar.common.logging.EventLogContext

import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry

import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.CentralDbTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import org.slf4j.LoggerFactory

import com.dogancaglar.paymentservice.domain.model.payment.Payment
import com.dogancaglar.paymentservice.domain.model.payment.ProcessingModel
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.id.PublicIdFactory
import com.dogancaglar.paymentservice.application.events.CaptureSuccessful
import com.dogancaglar.paymentservice.application.events.ExternalAsyncCaptureToPspPerformed
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.application.events.LedgerEntryEventData
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentCaptured
import com.dogancaglar.paymentservice.application.events.PostingDirection
import com.dogancaglar.paymentservice.application.events.PostingEventData
import com.dogancaglar.paymentservice.application.util.LedgerDomainEventEntityMapper
import com.dogancaglar.paymentservice.domain.model.ledger.Posting
import com.dogancaglar.paymentservice.domain.model.ledger.Tx
import com.dogancaglar.paymentservice.domain.model.ledger.Tx.CaptureTx
import com.dogancaglar.paymentservice.domain.model.ledger.TxStatus.PENDING
import com.dogancaglar.paymentservice.domain.model.ledger.TxStatus.SUCCESS
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType.MARKETPLACE_SUB_SELLER
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.payment.PaymentStatus.SENT_FOR_SETTLE
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxWriterPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort

open class PspResultProcessingService(
    private val centralDbTransactionalFacadePort: CentralDbTransactionalFacadePort,
    private val accountDirectory: AccountDirectoryPort,
    private val paymentTxPort: PaymentTxPort,
    private val idGeneratorPort: IdGeneratorPort,
    private val paymentRepository: PaymentRepository,
    private val localOutboxWriterPort: LocalOutboxWriterPort,
    private val serializationPort: SerializationPort
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun processAuthorized(event: PaymentAuthorized) {
        val amount = Amount.of(event.totalAmountValue, Currency(event.currency))
        val authReceivable = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        )
        val authLiability = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.AUTH_LIABILITY, "GLOBAL")
        )
        
        val paymentIdValue = idGeneratorPort.nextPaymentId()
        val txIdValue = idGeneratorPort.nextPaymentId()
        
        // 1. Generate Payment
        val splits = event.splits.map { it.toDomain() }
        val payment = Payment.initializeFromAuthEvent(
            paymentId = PaymentId(paymentIdValue),
            paymentIntentId = PaymentIntentId(event.paymentIntentId.toLongOrNull() ?: 0L),
            buyerId = BuyerId(event.buyerId),
            merchantAccountId = event.merchantAccountId,
            processingModel = ProcessingModel.valueOf(event.processingModel),
            totalAmount = amount,
            splits = splits
        )

        // 2. Generate AuthorizationTx
        val transaction = Tx.createAuthTx(
            txId = TxId(txIdValue),
            paymentId = PaymentId(paymentIdValue),
            paymentIntentId = PaymentIntentId(event.paymentIntentId.toLongOrNull() ?: 0L),
            acquirerReference = "",
            amount = amount,
            status = SUCCESS
        )

        // 3. Generate JournalEntries
        val journalEntries = JournalEntry.authHold(
            paymentId = PaymentId(paymentIdValue),
            txId = TxId(txIdValue),
            journalIdentifier = event.paymentIntentId,
            authorizedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability
        )


        // 4. Persist all
        centralDbTransactionalFacadePort.saveAtomically(payment, transaction, journalEntries)
    }

    fun processCaptured(event: PaymentCaptured, paymentIdValue: Long) {
        val amount = Amount.of(event.amountValue, Currency(event.currency))
        val targetEntityId = event.merchantAccountId
        val merchantGrossPool = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.MARKETPLACE_OPERATOR, targetEntityId)
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

        val txIdValue = idGeneratorPort.nextPaymentId()
        val txs = paymentTxPort.findByPaymentId(paymentIdValue)
        val authTx = txs.find { it.txType == "AUTHORIZATION" }
        val authTxIdValue = authTx?.txId ?: TxId(0L)
        
        val transaction = Tx.createCaptureTx(
            txId = TxId(txIdValue),
            paymentId = PaymentId(paymentIdValue),
            paymentIntentId = PaymentIntentId(event.paymentIntentId.toLongOrNull() ?: 0L),
            authorizationTxId = authTxIdValue,
            acquirerReference = "",
            amount = amount,
            status = PENDING
        )

        val journalEntries = JournalEntry.captureGrossAsset(
            paymentId = PaymentId(paymentIdValue),
            txId = TxId(txIdValue),
            journalIdentifier = event.publicPaymentIntentId,
            capturedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability,
            merchantGrossPool = merchantGrossPool,
            pspReceivable = pspReceivable
        )


        centralDbTransactionalFacadePort.saveAtomically(null, transaction, journalEntries)
    }



    fun processCapturePspPerformed(event: ExternalAsyncCaptureToPspPerformed) {
        val paymentIntentId = PaymentIntentId(event.paymentIntentId.toLongOrNull() ?: 0L)
        val payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
            ?: throw IllegalStateException("Payment not found for paymentIntentId=\${event.paymentIntentId}")

        // Update payment state
        val updatedPayment = payment.markSentForSettle()

        // Find existing Auth TX to link
        val txs = paymentTxPort.findByPaymentId(payment.paymentId.value)
        val authTx = txs.find { it.txType == "AUTHORIZATION" }
        val authTxIdValue = authTx?.txId ?: TxId(0L)

        // Insert CaptureTx as PENDING
        val txIdValue = event.captureTxId
        val amount = Amount.of(event.amountValue, Currency(event.currency))

        val captureTx = Tx.createCaptureTx(
            txId = TxId(txIdValue),
            paymentId = payment.paymentId,
            paymentIntentId = paymentIntentId,
            authorizationTxId = authTxIdValue,
            acquirerReference = "", // Not needed for pure outbox yet
            amount = amount,
            status = PENDING
        )

        centralDbTransactionalFacadePort.saveAtomically(updatedPayment, captureTx, emptyList())
    }

    fun processCaptureSuccessful(event: CaptureSuccessful) {
        val paymentIntentId = PaymentIntentId(PublicIdFactory.toInternalId(event.publicPaymentIntentId))
        val payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
            ?: throw IllegalStateException("Payment not found for paymentIntentId=\${event.publicPaymentIntentId}")

        // Invariant check: ensure it was sent for settle
        require(payment.status == SENT_FOR_SETTLE) {
            "Payment must be in SENT_FOR_SETTLE status, but was \${payment.status}"
        }

        // Apply capture to advance state to CAPTURED
        val amount = Amount.of(event.amountValue, Currency(event.currency))
        val capturedPayment = payment.applyCapture(amount)

        // Find pending Capture Tx and mark success
        val txs = paymentTxPort.findByPaymentId(payment.paymentId.value)
        val captureTx = txs.find { it.txType == "CAPTURE" && it.status == PENDING }
            ?: throw IllegalStateException("Pending CaptureTx not found for paymentId=\${payment.paymentId.value}")

        val updatedTx = (captureTx as CaptureTx).copy(
            status = SUCCESS
        )
        // Commit gross ledger distributions
        val merchantGrossPool = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.MARKETPLACE_OPERATOR, event.merchantAccountId))
        val authReceivable = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.AUTH_RECEIVABLE, "GLOBAL"))
        val authLiability = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.AUTH_LIABILITY, "GLOBAL"))
        val pspReceivable = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.PSP_RECEIVABLES, "GLOBAL"))

        val journalEntries = JournalEntry.captureGrossAsset(
            paymentId = payment.paymentId,
            txId = captureTx.txId,
            journalIdentifier = event.publicPaymentIntentId,
            capturedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability,
            merchantGrossPool = merchantGrossPool,
            pspReceivable = pspReceivable
        )

        // Emit an OutboxEvent containing the raw JournalEntries
        // This decouples marketplace split logic from basic capture processing
        val now = Utc.nowInstant()
        val ledgerEvent = LedgerEntriesRecorded.from(
            cmd = event,
            batchId = captureTx.txId.value.toString(),
            entries = journalEntries.map { LedgerDomainEventEntityMapper.toLedgerEntryEventData(it) },
            now = now
        )

        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = EventLogContext.getTraceId(),
            data = ledgerEvent,
            aggregateId = event.publicPaymentIntentId,
            parentEventId = EventLogContext.getEventId()
        )

        val outboxEvent = OutboxEvent.createNew(
            oeid = idGeneratorPort.nextPaymentId(),
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = serializationPort.toJson(envelope)
        )
        
        centralDbTransactionalFacadePort.saveAtomically(capturedPayment, updatedTx, journalEntries, listOf(outboxEvent))
    }

}
