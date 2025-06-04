package com.dogancaglar.paymentservice.domain.internal.model

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
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

    fun markAsPaid() = copy(status = PaymentStatus.SUCCESS)
    fun markAsFailed() = copy(status = PaymentStatus.FAILED)

    private fun copy(
        status: PaymentStatus = this.status,
        paymentOrders: List<PaymentOrder> = this.paymentOrders
    ): Payment = Payment(
        paymentId,
        publicPaymentId,
        buyerId,
        orderId,
        totalAmount,
        status,
        createdAt,
        paymentOrders
    )

    companion object {
        fun createNew(
            paymentId: Long,
            publicPaymentId: String,
            buyerId: String,
            orderId: String,
            totalAmount: Amount,
            createdAt: LocalDateTime,
            paymentOrders: List<PaymentOrder>
        ): Payment = Payment(
            paymentId,
            publicPaymentId,
            buyerId,
            orderId,
            totalAmount,
            PaymentStatus.INITIATED,
            createdAt,
            paymentOrders
        )

        fun reconstructFromPersistence(
            paymentId: Long,
            publicPaymentId: String,
            buyerId: String,
            orderId: String,
            totalAmount: Amount,
            status: PaymentStatus,
            createdAt: LocalDateTime,
            paymentOrders: List<PaymentOrder>
        ): Payment = Payment(
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
}