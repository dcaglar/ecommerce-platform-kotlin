package com.dogancaglar.paymentservice.adapter.persistence.entity

import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment_orders")
class PaymentOrderEntity(

    @Id
    @Column(name = "payment_order_id")
    val paymentOrderId: Long,

    @Column(name = "public_payment_order_id", nullable = false, unique = true)
    val publicPaymentOrderId: String,

    @Column(name = "payment_id", nullable = false)
    val paymentId: Long,

    @Column(name = "public_payment_id", nullable = false)
    val publicPaymentId: String,

    @Column(name = "seller_id", nullable = false)
    val sellerId: String,

    @Column(name = "amount_value", nullable = false)
    val amountValue: BigDecimal,

    @Column(name = "amount_currency", nullable = false)
    val amountCurrency: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: PaymentOrderStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime? = LocalDateTime.now(),

    @Column(name = "retry_count")
    val retryCount: Int = 0,

    @Column(name = "retry_reason")
    val retryReason: String? = null,

    @Column(name = "last_error_message")
    val lastErrorMessage: String? = null
)