package com.dogancaglar.paymentservice.adapter.persistance

import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment")
class PaymentEntity(

    @Id
    @Column(name = "payment_id")  // 👈 Add this line
    val paymentId: String,

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
    constructor(paymentId: String) : this(
        paymentId,
        "", BigDecimal.ZERO, "EUR", "", PaymentStatus.INITIATED, LocalDateTime.MIN
    )
}