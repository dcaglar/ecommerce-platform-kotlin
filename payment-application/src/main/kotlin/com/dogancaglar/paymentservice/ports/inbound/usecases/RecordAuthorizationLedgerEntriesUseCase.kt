package com.dogancaglar.paymentservice.ports.inbound.usecases

import com.dogancaglar.paymentservice.application.command.LedgerRecordingAuthorizationCommand

interface RecordAuthorizationLedgerEntriesUseCase {
    fun recordAuthorization(command: LedgerRecordingAuthorizationCommand)
}