package com.dogancaglar.paymentservice.adapter.persistance

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
data class PaymentOrderEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val sellerId: String,

    @Column(nullable = false)
    val amountValue: BigDecimal,

    @Column(nullable = false)
    val amountCurrency: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    val payment: PaymentEntity
)