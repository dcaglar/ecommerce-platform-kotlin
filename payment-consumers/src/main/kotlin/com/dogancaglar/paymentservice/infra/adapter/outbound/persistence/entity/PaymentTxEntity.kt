package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity

import java.time.Instant

data class PaymentTxEntity(
    val txId: Long,
    val txType: String,
    val parentTxId: Long?,
    val paymentId: Long,
    val paymentOrderId: Long?,
    val acquirerReference: String,
    val amountValue: Long,
    val amountCurrency: String,
    val createdAt: Instant? = null
)
