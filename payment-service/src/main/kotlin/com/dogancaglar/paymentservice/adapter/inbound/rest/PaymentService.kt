package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.AuthorizationRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.PaymentRequestMapper
import com.dogancaglar.paymentservice.application.validator.PaymentValidator
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentIntentUseCase
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentResponseDTO
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentIntentUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val authorizePaymentIntentUseCase: AuthorizePaymentIntentUseCase,
    private val createPaymentIntentUseCase: CreatePaymentIntentUseCase,
    private val paymentValidator: PaymentValidator
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)



    fun createPaymentIntent(request: CreatePaymentIntentRequestDTO): CreatePaymentIntentResponseDTO {
        paymentValidator.validate(request)
        val cmd = PaymentRequestMapper.toCreatePaymentIntentCommand(request)
        val paymentIntent = createPaymentIntentUseCase.create(cmd)
        return PaymentRequestMapper.toPaymentResponseDto(paymentIntent)
    }

    fun authorizePayment(publicPaymentId:String,request: AuthorizationRequestDTO): CreatePaymentIntentResponseDTO {
        val cmd = PaymentRequestMapper.toAuthorizePaymentIntentCommand(publicPaymentId,request)
        val paymentIntent = authorizePaymentIntentUseCase.authorize(cmd)
        return PaymentRequestMapper.toPaymentResponseDto(paymentIntent)
    }

}