package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized


interface RequestLedgerRecordingUseCase {
    fun requestLedgerRecording(event: PaymentOrderFinalized)
}