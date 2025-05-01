package com.dogancaglar.ecommerceplatform.payment.service

import com.dogancaglar.ecommerceplatform.payment.api.dto.PaymentRequestDTO
import org.springframework.stereotype.Service

@Service
class PaymentService {

    /**
     * Dummy method to simulate payment creation
     */
    fun createPayment(paymentRequest: PaymentRequestDTO) {
        // Simulating payment creation
        println("Payment created for buyer ${paymentRequest.buyerId}, total amount: ${paymentRequest.totalAmount.value} ${paymentRequest.totalAmount.currency}")
    }
}