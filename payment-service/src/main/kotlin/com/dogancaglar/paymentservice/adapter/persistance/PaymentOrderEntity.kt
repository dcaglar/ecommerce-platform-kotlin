package com.dogancaglar.paymentservice.adapter.persistance

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
data class PaymentOrderEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: String? = null,

    @Column(nullable = false, unique = true)
    val paymentOrderId: String,

    @Column(nullable = false)
    val sellerId: String,

    @Column(nullable = false)
    val amountValue: BigDecimal,

    @Column(nullable = false)
    val amountCurrency: String,

    @Column(nullable = false)
    val status: String,

    @Column(nullable = false)
    val retryCount: Int,

    @Column(nullable = false)
    val createdAt: java.time.LocalDateTime,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", referencedColumnName = "payment_id", nullable = false)
    val payment: PaymentEntity
)