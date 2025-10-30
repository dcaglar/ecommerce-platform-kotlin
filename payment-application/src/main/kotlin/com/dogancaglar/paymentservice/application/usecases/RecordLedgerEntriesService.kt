package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.model.LedgerEntry
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.ports.inbound.RecordLedgerEntriesUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

open class RecordLedgerEntriesService(
    private val ledgerWritePort: LedgerEntryPort,
    private val eventPublisherPort: EventPublisherPort,
    private val clock: Clock
) : RecordLedgerEntriesUseCase {

    private val ledgerEntryFactory = LedgerEntryFactory(clock)

    override fun recordLedgerEntries(event: LedgerRecordingCommand) {
        val createdAt = LocalDateTime.now(clock)
        val traceId = LogContext.getTraceId() ?: UUID.randomUUID().toString()
        val parentEventId = LogContext.getEventId()

        val amount = Amount(event.amountValue, event.currency)
        val merchantAccount = Account(event.sellerId, AccountType.MERCHANT_ACCOUNT)
        val acquirerAccount = Account(event.sellerId, AccountType.ACQUIRER_ACCOUNT)

        val journalEntries = when (event.status.uppercase()) {
            "SUCCESSFUL_FINAL" -> JournalEntry.fullFlow(
                paymentOrderId = event.publicPaymentOrderId,
                amount = amount,
                merchantAccount = merchantAccount,
                acquirerAccount = acquirerAccount
            )
            "FAILED_FINAL", "FAILED" -> JournalEntry.failedPayment(
                paymentOrderId = event.publicPaymentOrderId,
                amount = amount
            )
            else -> return
        }

        if (journalEntries.isEmpty()) return

        // 1️⃣ Transform journals to LedgerEntry
        val ledgerEntries = journalEntries.map { ledgerEntryFactory.create(it) }

        // 2️⃣ Persist each entry via outbound port
        ledgerEntries.forEach { entry ->
            ledgerWritePort.appendLedgerEntry(entry)
        }

        // 3️⃣ Build and publish domain event
        val recordedEvent = LedgerEntriesRecorded(
            ledgerBatchId = "ledger-batch-${UUID.randomUUID()}",
            paymentOrderId = event.paymentOrderId,
            publicPaymentOrderId = event.publicPaymentOrderId,
            sellerId = event.sellerId,
            currency = event.currency,
            status = event.status,
            recordedAt = createdAt,
            entryCount = ledgerEntries.size,
            ledgerEntryIds = ledgerEntries.map { it.ledgerEntryId },
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