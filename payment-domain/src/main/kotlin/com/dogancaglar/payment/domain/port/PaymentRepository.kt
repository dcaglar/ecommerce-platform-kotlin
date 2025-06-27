package com.dogancaglar.payment.domain.port

import com.dogancaglar.payment.domain.model.Payment
import com.dogancaglar.payment.domain.model.vo.PaymentId


interface PaymentRepository {
    fun save(payment: Payment)
    fun getMaxPaymentId(): PaymentId

}
