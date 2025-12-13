package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.application.commands.LedgerRecordingAuthorizationCommand

interface RecordAuthorizationLedgerEntriesUseCase {
    fun recordAuthorization(command: LedgerRecordingAuthorizationCommand)
}