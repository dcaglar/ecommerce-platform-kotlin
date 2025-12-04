package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId


interface PaymentRepository {
    fun save(payment: Payment): Payment
     fun findById(paymentId: PaymentId): Payment
        fun getMaxPaymentId(): PaymentId
    fun updatePayment(payment: Payment)

}
