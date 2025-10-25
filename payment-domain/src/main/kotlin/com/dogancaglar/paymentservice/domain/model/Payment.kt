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
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var paymentId: PaymentId? = null
        private var publicPaymentId: String? = null
        private var buyerId: BuyerId? = null
        private var orderId: OrderId? = null
        private var totalAmount: Amount? = null
        private var status: PaymentStatus = PaymentStatus.INITIATED
        private var createdAt: LocalDateTime = LocalDateTime.now()
        private var paymentOrders: List<PaymentOrder> = listOf()

        fun paymentId(value: PaymentId) = apply { this.paymentId = value }
        fun publicPaymentId(value: String) = apply { this.publicPaymentId = value }
        fun buyerId(value: BuyerId) = apply { this.buyerId = value }
        fun orderId(value: OrderId) = apply { this.orderId = value }
        fun totalAmount(value: Amount) = apply { this.totalAmount = value }
        fun status(value: PaymentStatus) = apply { this.status = value }
        fun createdAt(value: LocalDateTime) = apply { this.createdAt = value }
        fun paymentOrders(value: List<PaymentOrder>) = apply { this.paymentOrders = value }

        fun buildNew(): Payment = Payment(
            paymentId = requireNotNull(paymentId),
            publicPaymentId = requireNotNull(publicPaymentId),
            buyerId = requireNotNull(buyerId),
            orderId = requireNotNull(orderId),
            totalAmount = requireNotNull(totalAmount),
            status = PaymentStatus.INITIATED,
            createdAt = createdAt,
            paymentOrders = paymentOrders
        )

        fun buildFromPersistence(): Payment = Payment(
            paymentId = requireNotNull(paymentId),
            publicPaymentId = requireNotNull(publicPaymentId),
            buyerId = requireNotNull(buyerId),
            orderId = requireNotNull(orderId),
            totalAmount = requireNotNull(totalAmount),
            status = status,
            createdAt = createdAt,
            paymentOrders = paymentOrders
        )

        fun build(): Payment = buildFromPersistence()
    }
}