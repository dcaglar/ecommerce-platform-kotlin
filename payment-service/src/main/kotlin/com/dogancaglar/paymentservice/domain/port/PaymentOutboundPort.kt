package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.model.Payment


interface PaymentOutboundPort {
    fun save(payment: com.dogancaglar.paymentservice.domain.model.Payment)
    fun findByPaymentId(id: Long): Payment?
    fun getMaxPaymentId(): Long

}
