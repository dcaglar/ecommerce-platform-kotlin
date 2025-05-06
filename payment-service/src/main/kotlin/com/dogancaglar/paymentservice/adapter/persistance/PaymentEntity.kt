package com.dogancaglar.paymentservice.adapter.persistance

import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
data class PaymentEntity(
    @Id
    val paymentId: String,

    @Column(nullable = false)
    val buyerId: String,

    @Column(nullable = false)
    val totalAmountValue: BigDecimal,

    @Column(nullable = false)
    val totalAmountCurrency: String,

    @Column(nullable = false)
    val orderId: String,

    @Column(nullable = false)
    val status: PaymentStatus,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)