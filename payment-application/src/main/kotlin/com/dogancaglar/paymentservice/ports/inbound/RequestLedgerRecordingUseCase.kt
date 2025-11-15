package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent


interface RequestLedgerRecordingUseCase {
    fun requestLedgerRecording(event: PaymentOrderEvent)
}