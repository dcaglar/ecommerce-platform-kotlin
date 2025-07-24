package com.dogancaglar.payment.application.port.outbound

import com.dogancaglar.payment.domain.model.Payment
import com.dogancaglar.payment.domain.model.vo.PaymentId


interface PaymentRepository {
    fun save(payment: Payment)
    fun getMaxPaymentId(): PaymentId

}
