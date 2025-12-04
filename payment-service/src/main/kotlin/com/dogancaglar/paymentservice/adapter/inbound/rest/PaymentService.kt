package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.AuthorizePaymentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.PaymentRequestMapper
import com.dogancaglar.paymentservice.application.validator.PaymentValidator
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentUseCase
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentResponseDTO
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val authorizePaymentUseCase: AuthorizePaymentUseCase,
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val paymentValidator: PaymentValidator
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)



    fun createPayment(request: CreatePaymentRequestDTO): PaymentResponseDTO {
        paymentValidator.validate(request)
        val cmd = PaymentRequestMapper.toCommand(request)
        val payment = createPaymentUseCase.create(cmd)
        return PaymentRequestMapper.toResponse(payment)
    }

    fun authorizePayment(request: AuthorizePaymentRequestDTO): PaymentResponseDTO {
        val cmd = PaymentRequestMapper.toCommand(request)
        val payment = authorizePaymentUseCase.authorize(cmd)
        return PaymentRequestMapper.toResponse(payment)
    }

}