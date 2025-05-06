package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.model.Payment


interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findById(id: String): Payment?
}