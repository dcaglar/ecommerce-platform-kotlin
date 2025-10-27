package com.dogancaglar.paymentservice.domain.commands

import java.time.LocalDateTime

data class LedgerRecordingCommand(
    override val paymentOrderId: String,
    override val publicPaymentOrderId: String,
    override val paymentId: String,
    override val publicPaymentId: String,
    override val sellerId: String,
    override val amountValue: Long,
    override val currency: String,
    override val status: String,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val updatedAt: LocalDateTime = LocalDateTime.now()
) : LedgerCommand