package com.dogancaglar.payment.domain.port

import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.vo.PaymentId
import com.dogancaglar.payment.domain.model.vo.PaymentOrderId
import com.dogancaglar.port.PaymentOrderRepository

class InMemoryPaymentOrderRepository : PaymentOrderRepository {
    private val orders = mutableListOf<PaymentOrder>()

    override fun save(paymentOrder: PaymentOrder) {
        orders.removeIf { it.paymentOrderId == paymentOrder.paymentOrderId }
        orders.add(paymentOrder)
    }

    override fun upsertAll(orders: List<PaymentOrder>) {
        orders.forEach { save(it) }
    }

    override fun countByPaymentId(paymentId: PaymentId): Long =
        orders.count { it.paymentId == paymentId }.toLong()

    override fun countByPaymentIdAndStatusIn(paymentId: PaymentId, statuses: List<String>): Long =
        orders.count { it.paymentId == paymentId && statuses.contains(it.status.name) }.toLong()

    override fun existsByPaymentIdAndStatus(paymentId: PaymentId, status: String): Boolean =
        orders.any { it.paymentId == paymentId && it.status.name == status }

    override fun getMaxPaymentOrderId(): PaymentOrderId =
        orders.maxByOrNull { it.paymentOrderId.value }?.paymentOrderId ?: PaymentOrderId(0L)
}
