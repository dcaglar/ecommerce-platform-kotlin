package com.dogancaglar.paymentservice.application.events

object EventType {
    const val PAYMENT_AUTHORIZED = "payment_authorized"
    const val CAPTURE_REQUESTED = "capture_requested"
    const val CAPTURE_SUBMITTED = "capture_submitted"
    const val CAPTURE_CONFIRMED = "capture_confirmed"
    const val INTERNAL_TRANSFER_COMMAND = "internal_transfer_command"
    const val JOURNAL_ENTRIES_RECORDED = "journal_entries_recorded"
}

