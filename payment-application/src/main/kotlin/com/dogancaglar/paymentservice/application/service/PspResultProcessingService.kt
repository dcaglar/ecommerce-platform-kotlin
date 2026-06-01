package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.time.Utc
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentCaptured
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefunded
import com.dogancaglar.common.event.Event
import com.dogancaglar.paymentservice.application.events.PaymentBaseEvent
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
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional

import com.dogancaglar.paymentservice.domain.model.payment.Payment
import com.dogancaglar.paymentservice.domain.model.payment.ProcessingModel
import com.dogancaglar.paymentservice.application.dto.PaymentSplitDto
import com.dogancaglar.paymentservice.domain.model.payment.PaymentSplit
import com.dogancaglar.paymentservice.domain.model.payment.BalanceAccountType
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository

open class PspResultProcessingService(
    private val ledgerWritePort: LedgerEntryPort,
    private val accountDirectory: AccountDirectoryPort,
    private val paymentTxPort: PaymentTxPort,
    private val idGeneratorPort: IdGeneratorPort,
    private val paymentRepository: PaymentRepository,
    private val localOutboxWriterPort: com.dogancaglar.paymentservice.ports.outbound.LocalOutboxWriterPort,
    private val serializationPort: com.dogancaglar.paymentservice.ports.outbound.SerializationPort
) {

    private val ledgerEntryFactory = LedgerEntryFactory()
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
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
            paymentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentId(paymentIdValue),
            paymentIntentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId(event.paymentIntentId.toLongOrNull() ?: 0L),
            buyerId = BuyerId(event.buyerId),
            merchantAccountId = event.merchantAccountId,
            processingModel = ProcessingModel.valueOf(event.processingModel),
            totalAmount = amount,
            splits = splits
        )

        // 2. Generate AuthorizationTx
        val transaction = com.dogancaglar.paymentservice.domain.model.ledger.Tx.createAuthTx(
            txId = com.dogancaglar.paymentservice.domain.model.vo.TxId(txIdValue),
            paymentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentId(paymentIdValue),
            paymentIntentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId(event.paymentIntentId.toLongOrNull() ?: 0L),
            acquirerReference = "",
            amount = amount,
            status = com.dogancaglar.paymentservice.domain.model.ledger.TxStatus.SUCCESS
        )

        // 3. Generate JournalEntries
        val journalEntries = JournalEntry.authHold(
            paymentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentId(paymentIdValue),
            journalIdentifier = event.paymentIntentId,
            authorizedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability
        )
        
        // 4. Persist all
        paymentRepository.save(payment)
        saveOnly(transaction, journalEntries)
    }

    @Transactional
    fun processCaptured(event: PaymentCaptured, paymentIdValue: Long) {
        val amount = Amount.of(event.amountValue, Currency(event.currency))
        val targetEntityId = event.merchantAccountId
        val merchantGrossPool = Account.fromProfile(
            accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, targetEntityId)
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
        val authTxIdValue = authTx?.txId ?: com.dogancaglar.paymentservice.domain.model.vo.TxId(0L)
        
        val transaction = com.dogancaglar.paymentservice.domain.model.ledger.Tx.createCaptureTx(
            txId = com.dogancaglar.paymentservice.domain.model.vo.TxId(txIdValue),
            paymentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentId(paymentIdValue),
            paymentIntentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId(event.paymentIntentId.toLongOrNull() ?: 0L),
            authorizationTxId = authTxIdValue,
            acquirerReference = "",
            amount = amount,
            status = com.dogancaglar.paymentservice.domain.model.ledger.TxStatus.PENDING
        )

        val journalEntries = JournalEntry.captureGrossAsset(
            txId = com.dogancaglar.paymentservice.domain.model.vo.TxId(txIdValue),
            journalIdentifier = event.publicPaymentIntentId,
            capturedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability,
            merchantGrossPool = merchantGrossPool,
            pspReceivable = pspReceivable
        )
        
        saveOnly(transaction, journalEntries)
    }

    @Transactional
    fun processRefunded(event: PaymentOrderRefunded, paymentIdValue: Long) {
        val amount = Amount.of(event.amountValue, Currency(event.currency))
        val merchantGrossPool = Account.fromProfile(
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

        val txIdValue = idGeneratorPort.nextPaymentId()
        val txs = paymentTxPort.findByPaymentId(paymentIdValue)
        val captureTx = txs.find { it.txType == "CAPTURE" }
        val captureTxIdValue = captureTx?.txId ?: com.dogancaglar.paymentservice.domain.model.vo.TxId(0L)
        
        val transaction = com.dogancaglar.paymentservice.domain.model.ledger.Tx.createRefundTx(
            txId = com.dogancaglar.paymentservice.domain.model.vo.TxId(txIdValue),
            paymentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentId(paymentIdValue),
            paymentIntentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId(event.paymentIntentId.toLongOrNull() ?: 0L),
            captureTxId = captureTxIdValue,
            acquirerReference = "",
            amount = amount,
            status = com.dogancaglar.paymentservice.domain.model.ledger.TxStatus.PENDING
        )

        val journalEntries = JournalEntry.refund(
            txId = com.dogancaglar.paymentservice.domain.model.vo.TxId(txIdValue),
            journalIdentifier = event.paymentOrderId,
            refundedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability,
            merchantGrossPool = merchantGrossPool,
            pspReceivable = pspReceivable
        )
        
        saveOnly(transaction, journalEntries)
    }

    @Transactional
    fun processCapturePspPerformed(event: com.dogancaglar.paymentservice.application.events.ExternalAsyncCaptureToPspPerformed) {
        val paymentIntentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId(event.paymentIntentId.toLongOrNull() ?: 0L)
        val payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
            ?: throw IllegalStateException("Payment not found for paymentIntentId=\${event.paymentIntentId}")

        // Update payment state
        val updatedPayment = payment.markSentForSettle()
        paymentRepository.updatePayment(updatedPayment)

        // Find existing Auth TX to link
        val txs = paymentTxPort.findByPaymentId(payment.paymentId.value)
        val authTx = txs.find { it.txType == "AUTHORIZATION" }
        val authTxIdValue = authTx?.txId ?: com.dogancaglar.paymentservice.domain.model.vo.TxId(0L)

        // Insert CaptureTx as PENDING
        val txIdValue = idGeneratorPort.nextPaymentId()
        val amount = Amount.of(event.amountValue, Currency(event.currency))

        val captureTx = com.dogancaglar.paymentservice.domain.model.ledger.Tx.createCaptureTx(
            txId = com.dogancaglar.paymentservice.domain.model.vo.TxId(txIdValue),
            paymentId = payment.paymentId,
            paymentIntentId = paymentIntentId,
            authorizationTxId = authTxIdValue,
            acquirerReference = "", // Not needed for pure outbox yet
            amount = amount,
            status = com.dogancaglar.paymentservice.domain.model.ledger.TxStatus.PENDING
        )

        paymentTxPort.save(captureTx)
    }

    @Transactional
    fun processCaptureSuccessful(event: com.dogancaglar.paymentservice.application.events.CaptureSuccessful) {
        val paymentIntentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId(com.dogancaglar.common.id.PublicIdFactory.toInternalId(event.publicPaymentIntentId))
        val payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
            ?: throw IllegalStateException("Payment not found for paymentIntentId=\${event.publicPaymentIntentId}")

        // Invariant check: ensure it was sent for settle
        require(payment.status == com.dogancaglar.paymentservice.domain.model.payment.PaymentStatus.SENT_FOR_SETTLE) {
            "Payment must be in SENT_FOR_SETTLE status, but was \${payment.status}"
        }

        // Apply capture to advance state to CAPTURED
        val amount = Amount.of(event.amountValue, Currency(event.currency))
        val capturedPayment = payment.applyCapture(amount)
        paymentRepository.updatePayment(capturedPayment)

        // Find pending Capture Tx and mark success
        val txs = paymentTxPort.findByPaymentId(payment.paymentId.value)
        val captureTx = txs.find { it.txType == "CAPTURE" && it.status == com.dogancaglar.paymentservice.domain.model.ledger.TxStatus.PENDING }
            ?: throw IllegalStateException("Pending CaptureTx not found for paymentId=\${payment.paymentId.value}")

        val updatedTx = (captureTx as com.dogancaglar.paymentservice.domain.model.ledger.Tx.CaptureTx).copy(
            status = com.dogancaglar.paymentservice.domain.model.ledger.TxStatus.SUCCESS
        )
        paymentTxPort.save(updatedTx)

        // Commit gross ledger distributions
        val merchantGrossPool = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, event.merchantAccountId))
        val authReceivable = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.AUTH_RECEIVABLE, "GLOBAL"))
        val authLiability = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.AUTH_LIABILITY, "GLOBAL"))
        val pspReceivable = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.PSP_RECEIVABLES, "GLOBAL"))

        val journalEntries = JournalEntry.captureGrossAsset(
            txId = captureTx.txId,
            journalIdentifier = event.publicPaymentIntentId,
            capturedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability,
            merchantGrossPool = merchantGrossPool,
            pspReceivable = pspReceivable
        )
        saveOnly(updatedTx, journalEntries) // updatedTx is already saved above, but this just re-saves without harm

        // Execute asynchronous Internal Split Generation (US 5.4)
        if (payment.splits.isEmpty()) {
            logger.info("DIRECT_MERCHANT transaction, no splits to execute.")
            return
        }

        logger.info("MARKETPLACE transaction, staging internal transfers for \${payment.splits.size} splits")
        val outboxEvents = mutableListOf<com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent>()
        for (split in payment.splits) {
            val transferRequest = com.dogancaglar.paymentservice.application.events.InternalTransferRequest.from(
                paymentIntentId = payment.paymentIntentId.value.toString(),
                publicPaymentIntentId = event.publicPaymentIntentId,
                sourceAccountId = event.merchantAccountId,
                targetAccountId = split.targetEntityId,
                amountValue = split.amount.quantity,
                currency = split.amount.currency.currencyCode,
                now = Utc.nowInstant()
            )

            val envelope = com.dogancaglar.common.event.EventEnvelopeFactory.envelopeFor(
                traceId = EventLogContext.getTraceId(),
                data = transferRequest,
                aggregateId = transferRequest.publicPaymentIntentId,
                parentEventId = EventLogContext.getEventId()
            )

            val outboxEvent = com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent.createNew(
                oeid = idGeneratorPort.nextPaymentId(),
                eventType = envelope.eventType,
                aggregateId = envelope.aggregateId,
                payload = serializationPort.toJson(envelope)
            )
            outboxEvents.add(outboxEvent)
        }
        
        localOutboxWriterPort.saveAll(outboxEvents)
    }

    @Transactional
    fun processInternalTransferRequest(event: com.dogancaglar.paymentservice.application.events.InternalTransferRequest) {
        val merchantGrossPool = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, event.sourceAccountId))
        
        val splitAmount = Amount.of(event.amountValue, Currency(event.currency))
        val targetAccount = Account.fromProfile(accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, event.targetAccountId))
        
        val journalIdentifier = "\${event.publicPaymentIntentId}-\${event.targetAccountId}"
        
        val journalEntry = com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry.executeSubSellerSplit(
            journalIdentifier = journalIdentifier,
            merchantGrossPool = merchantGrossPool,
            splits = listOf(
                com.dogancaglar.paymentservice.domain.model.payment.PaymentSplit.of(
                    amount = splitAmount,
                    targetAccountType = com.dogancaglar.paymentservice.domain.model.payment.BalanceAccountType.MARKETPLACE_SUB_SELLER,
                    targetEntityId = event.targetAccountId
                )
            ),
            resolveTargetAccount = { _, _ -> targetAccount }
        )
        
        val ledgerEntries = journalEntry.map { ledgerEntryFactory.create(it) }
        val persistedLedgerEntries = ledgerWritePort.postLedgerEntriesAtomic(ledgerEntries)
        if (persistedLedgerEntries.isEmpty()) {
            logger.warn("⚠️ No ledger entries were persisted for InternalTransferRequest (duplicate or error)")
        }
    }

    private fun saveOnly(transaction: com.dogancaglar.paymentservice.domain.model.ledger.Tx, journalEntries: List<JournalEntry>) {
        if (journalEntries.isEmpty()) return
        paymentTxPort.save(transaction)
        val ledgerEntries = journalEntries.map { ledgerEntryFactory.create(it) }
        val persistedLedgerEntries = ledgerWritePort.postLedgerEntriesAtomic(ledgerEntries)
        if (persistedLedgerEntries.isEmpty()) {
            logger.warn("⚠️ No ledger entries were persisted (duplicate or error)")
        }
    }
}
