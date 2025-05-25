package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.model.Payment


interface PaymentOutBoundPort {
    fun save(payment: Payment): Payment
    fun findByPaymentId(id: Long): Payment?

}