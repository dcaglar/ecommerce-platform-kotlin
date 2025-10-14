package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import java.time.LocalDateTime

class Payment private constructor(
    val paymentId: PaymentId,
    val publicPaymentId: String,
    val buyerId: BuyerId,
    val orderId: OrderId,
    val totalAmount: Amount,
    val status: PaymentStatus,
    val createdAt: LocalDateTime,
    val paymentOrders: List<PaymentOrder>
) {

    class Builder {
        private var paymentId: PaymentId = PaymentId(0)
        private var publicPaymentId: String = ""
        private var buyerId: BuyerId = BuyerId("")
        private var orderId: OrderId = OrderId("")
        private var totalAmount: Amount = Amount(0L, "USD") // Default value
        private var status: PaymentStatus = PaymentStatus.INITIATED
        private var createdAt: LocalDateTime = LocalDateTime.now()
        private var paymentOrders: List<PaymentOrder> = listOf()

        fun paymentId(paymentId: PaymentId) = apply { this.paymentId = paymentId }
        fun publicPaymentId(publicPaymentId: String) = apply { this.publicPaymentId = publicPaymentId }
        fun buyerId(buyerId: BuyerId) = apply { this.buyerId = buyerId }
        fun orderId(orderId: OrderId) = apply { this.orderId = orderId }
        fun totalAmount(totalAmount: Amount) = apply { this.totalAmount = totalAmount }
        fun status(status: PaymentStatus) = apply { this.status = status }
        fun createdAt(createdAt: LocalDateTime) = apply { this.createdAt = createdAt }
        fun paymentOrders(paymentOrders: List<PaymentOrder>) = apply { this.paymentOrders = paymentOrders }

        fun build(): Payment = Payment(
            paymentId = paymentId,
            publicPaymentId = publicPaymentId,
            buyerId = buyerId,
            orderId = orderId,
            totalAmount = totalAmount,
            status = status,
            createdAt = createdAt,
            paymentOrders = paymentOrders
        )
    }

    fun markAsPaid() = copy(status = PaymentStatus.SUCCESS)
    fun markAsFailed() = copy(status = PaymentStatus.FAILED)

    fun addPaymentOrder(paymentOrder: PaymentOrder): Payment =
        copy(paymentOrders = this.paymentOrders + paymentOrder)

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

            paymentId: PaymentId,
            publicPaymentId: String,
            buyerId: BuyerId,
            orderId: OrderId,
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
            paymentId: PaymentId,
            publicPaymentId: String,
            buyerId: BuyerId,
            orderId: OrderId,
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