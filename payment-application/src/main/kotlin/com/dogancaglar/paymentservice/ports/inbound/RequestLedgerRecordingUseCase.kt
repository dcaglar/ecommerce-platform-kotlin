package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent

interface RequestLedgerRecordingUseCase {
    fun requestLedgerRecording(event: PaymentOrderEvent)
}