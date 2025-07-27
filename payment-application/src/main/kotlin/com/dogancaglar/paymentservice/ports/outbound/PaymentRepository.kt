package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId


interface PaymentRepository {
    fun save(payment: Payment)
    fun getMaxPaymentId(): PaymentId

}
