package com.dogancaglar.paymentservice.domain.event

import java.time.LocalDateTime

data class LedgerEntriesRecorded(
    override val ledgerBatchId: String,
    override val paymentOrderId: String,
    override val publicPaymentOrderId: String,
    override val sellerId: String,
    override val currency: String,
    override val status: String,
    override val recordedAt: LocalDateTime,
    val entryCount: Int,
    val ledgerEntryIds: List<Long> = emptyList(),
    override val traceId: String? = null,
    override val parentEventId: String? = null
) : LedgerEvent