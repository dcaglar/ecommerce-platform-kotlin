package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.PaymentRequestMapper
import com.dogancaglar.paymentservice.application.validator.PaymentValidator
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentUseCase
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentResponseDTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val authorizePaymentUseCase: AuthorizePaymentUseCase,
    private val paymentValidator: PaymentValidator
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)



    fun createPayment(request: PaymentRequestDTO): PaymentResponseDTO {
        paymentValidator.validate(request)
        val cmd = PaymentRequestMapper.toCommand(request)
        val payment = authorizePaymentUseCase.authorize(cmd)
        return PaymentRequestMapper.toResponse(payment)
    }

}