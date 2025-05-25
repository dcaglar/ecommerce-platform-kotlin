package com.dogancaglar.paymentservice.adapter.persistence

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

    @Column(name = "public_id", nullable = false, unique = true)
    val publicId: String,

    @Column(nullable = false)
    val buyerId: String,

    @Column(nullable = false)
    val totalAmountValue: BigDecimal,

    @Column(nullable = false)
    val totalAmountCurrency: String,

    @Column(nullable = false)
    val orderId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: PaymentStatus,
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    constructor(paymentId: Long, publicId: String) : this(
        paymentId = paymentId,
        publicId = publicId,
        buyerId = "",
        totalAmountValue = BigDecimal.ZERO,
        totalAmountCurrency = "EUR",
        orderId = "",
        status = PaymentStatus.INITIATED,
        createdAt = LocalDateTime.MIN
    )
}