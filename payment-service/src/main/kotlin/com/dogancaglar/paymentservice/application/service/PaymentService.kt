package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.port.PaymentRepository
import org.springframework.stereotype.Service


@Service
class PaymentService(
    private val paymentRepository: PaymentRepository
) {
    fun createPayment(payment: Payment): Payment {
        return paymentRepository.save(payment)
    }
}

