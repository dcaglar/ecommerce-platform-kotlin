package com.dogancaglar.paymentservice.adapter.persistence.entity

import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
class PaymentEntity(

    @Id
    @Column(name = "payment_id")  // 👈 Add this line
    val paymentId: Long,

    @Column(name = "public_payment_id", nullable = false, unique = true)
    val publicPaymentId: String,

    @Column(nullable = false)
    val buyerId: String,

    @Column(name = "amount_value", nullable = false)
    val totalAmountValue: BigDecimal,

    @Column(name = "amount_currency", nullable = false)
    val totalAmountCurrency: String,

    @Column(nullable = false)
    val orderId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: PaymentStatus,
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    constructor(paymentId: Long, publicPaymentId: String) : this(
        paymentId = paymentId,
        publicPaymentId = publicPaymentId,
        buyerId = "",
        totalAmountValue = BigDecimal.ZERO,
        totalAmountCurrency = "EUR",
        orderId = "",
        status = PaymentStatus.INITIATED,
        createdAt = LocalDateTime.now()
    )
}