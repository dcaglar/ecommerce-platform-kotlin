package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.LocalDateTime

data class PaymentOrderStatusCheckEntity internal constructor(
    val id: Long = 0,
    val paymentOrderId: Long,
    val scheduledAt: LocalDateTime,
    val attempt: Int = 1,
    val status: String = "SCHEDULED",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)