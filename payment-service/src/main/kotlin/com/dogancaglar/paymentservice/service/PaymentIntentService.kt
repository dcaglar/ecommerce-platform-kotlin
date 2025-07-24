package com.dogancaglar.paymentservice.service

import com.dogancaglar.payment.application.port.inbound.CreatePaymentUseCase
import com.dogancaglar.payment.domain.model.CreatePaymentCommand
import com.dogancaglar.payment.domain.model.Payment
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentResponseDTO
import com.dogancaglar.paymentservice.web.mapper.PaymentRequestMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentIntentService(
    private val createPaymentUseCase: CreatePaymentUseCase,
) {
    fun createPaymentIntent(request: PaymentRequestDTO): PaymentResponseDTO {
        val command = PaymentRequestMapper.toCommand(request)
        val payment = createPaymentTransactional(command);
        return PaymentRequestMapper.toResponse(payment)
    }

    //it should be transactional
    @Transactional
    private fun createPaymentTransactional(createPaymentCommand: CreatePaymentCommand): Payment {
        val persistedPaymentIntent = createPaymentUseCase.create(createPaymentCommand);
        return persistedPaymentIntent;
    }
}
