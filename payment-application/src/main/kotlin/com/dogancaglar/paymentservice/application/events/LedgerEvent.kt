package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.common.event.Event
import java.time.LocalDateTime

/**
 * Marker interface for all ledger-related events.
 * Mirrors the structure of PaymentOrderEvent for consistency.
 */
interface LedgerEvent : Event {
    val ledgerBatchId: String
    val paymentOrderId: String
    val sellerId: String
    val currency: String
    val status: String
    val recordedAt: LocalDateTime
    val traceId: String?
    val parentEventId: String?
}