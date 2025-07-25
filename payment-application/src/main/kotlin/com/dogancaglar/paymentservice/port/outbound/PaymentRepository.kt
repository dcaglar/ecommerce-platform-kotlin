package com.dogancaglar.paymentservice.port.outbound

import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId


interface PaymentRepository {
    fun save(payment: Payment)
    fun getMaxPaymentId(): PaymentId

}
