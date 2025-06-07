package com.dogancaglar.paymentservice.domain.internal.model

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime

class Payment private constructor(
    val paymentId: Long,
    val publicPaymentId: String,
    val buyerId: String,
    val orderId: String,
    val totalAmount: Amount,
    val status: PaymentStatus,
    val createdAt: LocalDateTime,
    val paymentOrders: List<PaymentOrder>
) {


    class Builder {
        private var paymentId: Long = 0
        private var publicPaymentId: String = ""
        private var buyerId: String = ""
        private var orderId: String = ""
        private var totalAmount: Amount = Amount(BigDecimal.ZERO, "USD") // Default value
        private var status: PaymentStatus = PaymentStatus.INITIATED
        private var createdAt: LocalDateTime = LocalDateTime.now()
        private var paymentOrders: List<PaymentOrder> = listOf()

        fun paymentId(paymentId: Long) = apply { this.paymentId = paymentId }
        fun publicPaymentId(publicPaymentId: String) = apply { this.publicPaymentId = publicPaymentId }
        fun buyerId(buyerId: String) = apply { this.buyerId = buyerId }
        fun orderId(orderId: String) = apply { this.orderId = orderId }
        fun totalAmount(totalAmount: Amount) = apply { this.totalAmount = totalAmount }
        fun status(status: PaymentStatus) = apply { this.status = status }
        fun createdAt(createdAt: LocalDateTime) = apply { this.createdAt = createdAt }
        fun paymentOrders(paymentOrders: List<PaymentOrder>) = apply { this.paymentOrders = paymentOrders }

        fun build(): Payment = Payment(
            paymentId,
            publicPaymentId,
            buyerId,
            orderId,
            totalAmount,
            status,
            createdAt,
            paymentOrders
        )
    }

    fun markAsPaid() = copy(status = PaymentStatus.SUCCESS)
    fun markAsFailed() = copy(status = PaymentStatus.FAILED)

    private fun copy(
        status: PaymentStatus = this.status,
        paymentOrders: List<PaymentOrder> = this.paymentOrders
    ): Payment = Builder()
        .paymentId(paymentId)
        .publicPaymentId(publicPaymentId)
        .buyerId(buyerId)
        .orderId(orderId)
        .totalAmount(totalAmount)
        .status(status)
        .createdAt(createdAt)
        .paymentOrders(paymentOrders)
        .build()

    companion object {
        fun createNew(
            paymentId: Long,
            publicPaymentId: String,
            buyerId: String,
            orderId: String,
            totalAmount: Amount,
            createdAt: LocalDateTime,
            paymentOrders: List<PaymentOrder>
        ): Payment = Builder()
            .paymentId(paymentId)
            .publicPaymentId(publicPaymentId)
            .buyerId(buyerId)
            .orderId(orderId)
            .totalAmount(totalAmount)
            .status(PaymentStatus.INITIATED)
            .createdAt(createdAt)
            .paymentOrders(paymentOrders)
            .build()

        fun reconstructFromPersistence(
            paymentId: Long,
            publicPaymentId: String,
            buyerId: String,
            orderId: String,
            totalAmount: Amount,
            status: PaymentStatus,
            createdAt: LocalDateTime,
            paymentOrders: List<PaymentOrder>
        ): Payment = Payment.Builder()
            .paymentId(paymentId)
            .publicPaymentId(publicPaymentId)
            .buyerId(buyerId)
            .orderId(orderId)
            .totalAmount(totalAmount)
            .status(status)
            .createdAt(createdAt)
            .paymentOrders(paymentOrders)
            .build()
    }
}