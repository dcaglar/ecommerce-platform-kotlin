package com.dogancaglar.paymentservice.domain.event

import java.time.LocalDateTime

/**
 * Marker interface for all ledger-related events.
 * Mirrors the structure of PaymentOrderEvent for consistency.
 */
interface LedgerEvent {
    val ledgerBatchId: String
    val paymentOrderId: String
    val publicPaymentOrderId: String
    val sellerId: String
    val currency: String
    val status: String
    val recordedAt: LocalDateTime
    val traceId: String?
    val parentEventId: String?
}