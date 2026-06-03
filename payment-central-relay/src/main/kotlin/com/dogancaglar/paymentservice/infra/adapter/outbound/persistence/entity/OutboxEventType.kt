package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity

import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.CaptureReceived
import com.dogancaglar.paymentservice.application.events.ExternalAsyncCaptureToPspPerformed
import com.dogancaglar.paymentservice.application.events.CaptureSuccessful
import com.dogancaglar.paymentservice.application.events.InternalTransferRequest
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded

enum class OutboxEventType(val eventClass: Class<*>) {
    payment_authorized(PaymentAuthorized::class.java),
    capture_received(CaptureReceived::class.java),
    external_async_capture_psp_performed(ExternalAsyncCaptureToPspPerformed::class.java),
    capture_successful(CaptureSuccessful::class.java),
    internal_transfer_request(InternalTransferRequest::class.java),
    ledger_entries_recorded(LedgerEntriesRecorded::class.java);

    //todo implem,ent paymentresultconsumer
    // extend doutboxrelayjob so it can also prtocess new event types,

    companion object {
        fun from(value: String): OutboxEventType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}