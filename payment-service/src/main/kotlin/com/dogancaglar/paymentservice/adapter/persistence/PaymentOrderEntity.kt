package com.dogancaglar.paymentservice.adapter.persistence

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Table(name = "payment_orders")
@Entity
data class PaymentOrderEntity(
    @Id
    @Column(name = "payment_order_id")
    val paymentOrderId: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    val sellerId: String,

    @Column(nullable = false)
    val amountValue: BigDecimal,

    @Column(nullable = false)
    val amountCurrency: String,

    @Column(nullable = false)
    val status: String,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(nullable = false)
    val createdAt: java.time.LocalDateTime,

    @Column(nullable = false)
    val updatedAt: java.time.LocalDateTime?= LocalDateTime.now(),

    @Column(name = "retry_reason")
    val retryReason :String?="",
    @Column(name = "last_error_message")
    val lastErrorMessage :String="",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", referencedColumnName = "payment_id", nullable = false)
    val payment: PaymentEntity
)