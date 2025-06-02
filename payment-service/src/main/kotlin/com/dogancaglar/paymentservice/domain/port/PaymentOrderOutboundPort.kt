package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder


interface PaymentOrderOutboundPort {
    fun save(paymentOrder: PaymentOrder)
    fun saveAll(orders: List<PaymentOrder>)
    fun countByPaymentId(paymentId: Long): Long
    fun countByPaymentIdAndStatusIn(paymentId: Long, statuses: List<String>): Long
    fun existsByPaymentIdAndStatus(paymentId: Long, status: String): Boolean
    fun getMaxPaymentOrderId(): Long
}