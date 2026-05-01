package com.dogancaglar.paymentservice.ports.inbound.usecases

import com.dogancaglar.paymentservice.application.command.LedgerRecordingCommand


/**
 * Defines the use case for creating ledger entries based on a LedgerRecordingCommand
 */
interface RecordLedgerEntriesUseCase {
    fun recordLedgerEntries(event: LedgerRecordingCommand)
}