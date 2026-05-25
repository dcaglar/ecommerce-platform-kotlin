package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.paymentservice.application.command.LedgerRecordingAuthorizationCommand
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.Account
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalEntry
import com.dogancaglar.paymentservice.domain.util.LedgerEntryFactory
import com.dogancaglar.paymentservice.ports.inbound.usecases.RecordAuthorizationLedgerEntriesUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import org.slf4j.LoggerFactory

class RecordAuthorizationLedgerEntriesService(
    private val ledgerWritePort: LedgerEntryPort,
    private val accountDirectoryPort: AccountDirectoryPort
) : RecordAuthorizationLedgerEntriesUseCase {
    private val ledgerEntryFactory = LedgerEntryFactory()
    private val logger = LoggerFactory.getLogger(javaClass)


    override fun recordAuthorization(event: LedgerRecordingAuthorizationCommand) {

        val amount = Amount.of(event.amountValue, Currency(event.currency))
        val authReceivable = Account.fromProfile(
            accountDirectoryPort.getAccountProfile(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        )
        val authLiability = Account.fromProfile(
            accountDirectoryPort.getAccountProfile(AccountType.AUTH_LIABILITY, "GLOBAL")
        )
        val journalEntries: List<JournalEntry> =JournalEntry.authHold(
            journalIdentifier = event.paymentId,
            authorizedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability
        )

        if (journalEntries.isEmpty()) return

        // 1️⃣ Transform journals to LedgerEntry
        val ledgerEntries = journalEntries.map { ledgerEntryFactory.create(it) }

        val persistedLedgerEntries = ledgerWritePort.postLedgerEntriesAtomic(ledgerEntries)
        if (persistedLedgerEntries.isEmpty()) {
            logger.warn("⚠️ No ledger entries were persisted (duplicate or error)")
            return
        }
    }
}