package com.dogancaglar.paymentservice.adapter.persistence.entity

import java.time.LocalDateTime
import jakarta.persistence.*

@Entity
@Table(name = "payment_order_status_checks")
data class PaymentOrderStatusCheckEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "payment_order_id", nullable = false)
    val paymentOrderId: Long,

    @Column(name = "scheduled_at", nullable = false)
    val scheduledAt: LocalDateTime,

    @Column(name = "attempt", nullable = false)
    val attempt: Int = 1,

    @Column(name = "status", nullable = false)
    val status: String = "SCHEDULED",

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)