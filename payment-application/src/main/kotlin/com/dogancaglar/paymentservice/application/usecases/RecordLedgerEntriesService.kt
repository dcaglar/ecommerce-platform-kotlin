package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.application.metadata.EventMetadatas
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
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

open class RecordLedgerEntriesService(
    private val ledgerWritePort: LedgerEntryPort,
    private val eventPublisherPort: EventPublisherPort,
    private val accountDirectory: AccountDirectoryPort,
    private val clock: Clock
) : RecordLedgerEntriesUseCase {

    private val ledgerEntryFactory = LedgerEntryFactory(clock)
    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)

    override fun recordLedgerEntries(event: LedgerRecordingCommand) {
        val createdAt = LocalDateTime.now(clock)
        val traceId = LogContext.getTraceId() ?: UUID.randomUUID().toString()
        val parentEventId = LogContext.getEventId()

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

        val journalEntries: List<JournalEntry> = when (event.status.uppercase()) {
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

        // 4️⃣ Build and publish domain event
        val recordedEvent = LedgerEntriesRecorded.create(
            ledgerBatchId = "ledger-batch-${UUID.randomUUID()}",
            paymentOrderId = event.paymentOrderId,
            sellerId = event.sellerId,
            currency = event.currency,
            status = event.status,
            recordedAt = createdAt,
            ledgerEntries = ledgerEntryEventDataList,
            traceId = traceId,
            parentEventId = parentEventId?.toString()
        )

        eventPublisherPort.publishSync(
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = event.sellerId,
            data = recordedEvent,
            parentEventId = parentEventId,
            traceId = traceId
        )
    }


}