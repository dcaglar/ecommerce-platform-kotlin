package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.time.Utc
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.command.LedgerRecordingCommand
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.application.util.LedgerDomainEventEntityMapper
import com.dogancaglar.paymentservice.domain.util.LedgerEntryFactory
import com.dogancaglar.paymentservice.ports.inbound.usecases.RecordLedgerEntriesUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import org.slf4j.LoggerFactory
import java.util.UUID

open class RecordLedgerEntriesService(
    private val ledgerWritePort: LedgerEntryPort,
    private val eventPublisherPort: EventPublisherPort,
    private val accountDirectory: AccountDirectoryPort,
    private val paymentTxPort: PaymentTxPort,
    private val idGeneratorPort: IdGeneratorPort) : RecordLedgerEntriesUseCase {

    private val ledgerEntryFactory = LedgerEntryFactory()
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun recordLedgerEntries(event: LedgerRecordingCommand) {
        val createdAt = Utc.nowLocalDateTime()
        val traceId = EventLogContext.getTraceId()
        val parentEventId = EventLogContext.getEventId()

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

        val journalEntries: List<JournalEntry> = when (event.finalStatus.uppercase()) {
            PaymentOrderStatus.CAPTURED.name -> {
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
                captureResult.journalEntries
            }

            "FAILED_FINAL", "FAILED" -> JournalEntry.failedPayment(
                paymentOrderId = event.paymentOrderId,
                amount = amount
            )
            else -> return
        }

        if (journalEntries.isEmpty()) return

        // 1️⃣ Transform journals to LedgerEntry
        val ledgerEntries = journalEntries.map { ledgerEntryFactory.create(it) }

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



        val recordedEvent = LedgerEntriesRecorded.from(event,"ledger-batch-${UUID.randomUUID()}",ledgerEntryEventDataList,Utc.toInstant(createdAt))

        eventPublisherPort.publishSync(
            aggregateId = EventLogContext.getAggregateId()!!,
            data = recordedEvent,
            parentEventId = EventLogContext.getEventId(),
            traceId = EventLogContext.getTraceId()
        )
    }


}