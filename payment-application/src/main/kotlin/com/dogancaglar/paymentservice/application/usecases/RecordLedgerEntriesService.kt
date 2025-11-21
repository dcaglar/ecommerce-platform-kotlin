package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.time.Utc
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.application.util.LedgerDomainEventMapper
import com.dogancaglar.paymentservice.domain.util.LedgerEntryFactory
import com.dogancaglar.paymentservice.ports.inbound.RecordLedgerEntriesUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

open class RecordLedgerEntriesService(
    private val ledgerWritePort: LedgerEntryPort,
    private val eventPublisherPort: EventPublisherPort,
    private val accountDirectory: AccountDirectoryPort) : RecordLedgerEntriesUseCase {

    private val ledgerEntryFactory = LedgerEntryFactory()
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun recordLedgerEntries(event: LedgerRecordingCommand) {
        val createdAt = Utc.nowLocalDateTime()
        val traceId = EventLogContext.getTraceId() ?: UUID.randomUUID().toString()
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
            PaymentStatus.AUTHORIZED.name ->
                    JournalEntry.authHold(
                        journalIdentifier = event.paymentId,
                        authorizedAmount = amount,
                        authReceivable = authReceivable,
                        authLiability = authLiability
            )

            PaymentOrderStatus.CAPTURED.name ->
                JournalEntry.capture(journalIdentifier = event.paymentOrderId,
                    capturedAmount = amount,
                    authReceivable=authReceivable,
                    authLiability=authLiability,
                    merchantAccount = merchantAccount,
                    pspReceivable = pspReceivable
                    )


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
            LedgerDomainEventMapper.toLedgerEntryEventData(it)
        }



        val recordedEvent = LedgerEntriesRecorded.from(event,"ledger-batch-${UUID.randomUUID()}",ledgerEntryEventDataList,Utc.toInstant(createdAt))

        eventPublisherPort.publishSync(
            aggregateId = event.sellerId,
            data = recordedEvent,
            parentEventId = parentEventId,
            traceId = traceId
        )
    }


}