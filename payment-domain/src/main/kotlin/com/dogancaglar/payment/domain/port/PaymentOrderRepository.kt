package com.dogancaglar.port

import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.vo.PaymentId
import com.dogancaglar.payment.domain.model.vo.PaymentOrderId


interface PaymentOrderRepository {
    fun save(paymentOrder: PaymentOrder)
    fun saveAll(orders: List<PaymentOrder>)
    fun countByPaymentId(paymentId: PaymentId): Long
    fun countByPaymentIdAndStatusIn(paymentId: PaymentId, statuses: List<String>): Long
    fun existsByPaymentIdAndStatus(paymentId: PaymentId, status: String): Boolean
    fun getMaxPaymentOrderId(): PaymentOrderId
}