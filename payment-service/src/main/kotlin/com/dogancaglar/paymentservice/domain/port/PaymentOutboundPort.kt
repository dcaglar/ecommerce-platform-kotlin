package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.paymentservice.domain.internal.model.Payment


interface PaymentOutboundPort {
    fun save(payment: Payment)
    fun getMaxPaymentId(): Long

}
