package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId


interface PaymentRepository {
    fun saveIdempotent(payment: Payment): Payment
     fun findByIdempotencyKey(key: String): Payment?
     fun getMaxPaymentId(): PaymentId
    fun updatePayment(payment: Payment)

}
