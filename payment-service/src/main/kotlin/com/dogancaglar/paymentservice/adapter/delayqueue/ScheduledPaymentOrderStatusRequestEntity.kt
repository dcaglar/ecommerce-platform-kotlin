package com.dogancaglar.paymentservice.adapter.delayqueue

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "scheduled_payment_order_request_repository")
data class ScheduledPaymentOrderStatusRequestEntity(
    @Id
    val id: String=UUID.randomUUID().toString(),


    @Column(name = "payload",columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "send_after")
    val sendAfter: Instant,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)