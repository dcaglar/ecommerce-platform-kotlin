package com.dogancaglar.paymentservice.port.outbound

import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId


interface PaymentOrderRepository {
    fun save(paymentOrder: PaymentOrder)
    fun upsertAll(orders: List<PaymentOrder>)
    fun countByPaymentId(paymentId: PaymentId): Long
    fun countByPaymentIdAndStatusIn(paymentId: PaymentId, statuses: List<String>): Long
    fun existsByPaymentIdAndStatus(paymentId: PaymentId, status: String): Boolean
    fun getMaxPaymentOrderId(): PaymentOrderId
}