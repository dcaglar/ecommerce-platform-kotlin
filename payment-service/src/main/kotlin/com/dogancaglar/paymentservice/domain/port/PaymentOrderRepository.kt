package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.model.PaymentOrder


interface PaymentOrderRepository {
    fun save(paymentOrder: PaymentOrder)
    fun saveAll(orders: List<PaymentOrder>)
    fun findById(id: String): PaymentOrder?
    fun countByPaymentId(paymentId: String): Long
    fun countByPaymentIdAndStatusIn(paymentId: String, statuses: List<String>): Long
    fun existsByPaymentIdAndStatus(paymentId: String, status: String): Boolean
}