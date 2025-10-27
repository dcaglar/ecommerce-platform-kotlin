package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.model.LedgerEntry
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntryFactory
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

    override fun recordLedgerEntries(event: LedgerRecordingCommand) {
        val createdAt = LocalDateTime.now(clock)
        val traceId = LogContext.getTraceId() ?: UUID.randomUUID().toString()
        val parentEventId = LogContext.getEventId()

        val amount = Amount(event.amountValue, event.currency)
        val merchantAccount = Account(event.sellerId, AccountType.MERCHANT_ACCOUNT)
        val acquirerAccount = Account(event.sellerId, AccountType.ACQUIRER_ACCOUNT)

        // 1️⃣ Create journal entries
        val entries = when (event.status.uppercase()) {
            "SUCCESSFUL_FINAL" -> JournalEntryFactory.fullFlow(
                paymentOrderId = event.publicPaymentOrderId,
                amount = amount,
                merchantAccount = merchantAccount,
                acquirerAccount = acquirerAccount
            )
            "FAILED_FINAL", "FAILED" -> JournalEntryFactory.failedPayment(
                paymentOrderId = event.publicPaymentOrderId,
                amount = amount
            )
            else -> return
        }

        // 👇 Skip persistence and publishing if there is nothing to record
        if (entries.isEmpty()) return

        // 2️⃣ Persist all entries under one batch ID
        val ledgerBatchId = "ledger-batch-${UUID.randomUUID()}"
        val persistedIds = entries.map { entry ->
            val ledgerEntry = LedgerEntry(
                ledgerEntryId = 0L,
                journalEntry = entry,
                createdAt = createdAt
            )
            ledgerWritePort.appendLedgerEntry(ledgerEntry)
            ledgerEntry.ledgerEntryId
        }

        // 3️⃣ Build domain event for downstream consumers
        val recordedEvent = LedgerEntriesRecorded(
            ledgerBatchId = ledgerBatchId,
            paymentOrderId = event.paymentOrderId,
            publicPaymentOrderId = event.publicPaymentOrderId,
            sellerId = event.sellerId,
            currency = event.currency,
            status = event.status,
            recordedAt = createdAt,
            entryCount = entries.size,
            ledgerEntryIds = persistedIds,
            traceId = traceId,
            parentEventId = parentEventId?.toString()
        )

        // 4️⃣ Publish the confirmation event
        eventPublisherPort.publishSync(
            eventMetaData = EventMetadatas.LedgerEntriesRecordedMetadata,
            aggregateId = event.publicPaymentOrderId,
            data = recordedEvent,
            parentEventId = parentEventId,
            traceId = traceId
        )
    }
}