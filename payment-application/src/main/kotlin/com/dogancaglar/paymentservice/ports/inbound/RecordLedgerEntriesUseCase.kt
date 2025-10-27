package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand


/**
 * Defines the use case for creating ledger entries based on a LedgerRecordingCommand
 */
interface RecordLedgerEntriesUseCase {
    fun recordLedgerEntries(event: LedgerRecordingCommand)
}