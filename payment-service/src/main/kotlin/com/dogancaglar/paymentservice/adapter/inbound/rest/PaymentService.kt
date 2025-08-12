package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.PaymentRequestMapper
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import com.dogancaglar.port.out.web.dto.PaymentRequestDTO
import com.dogancaglar.port.out.web.dto.PaymentResponseDTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val createPaymentUseCase: CreatePaymentUseCase
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    fun createPayment(request: PaymentRequestDTO): PaymentResponseDTO {
        try {
            val command = PaymentRequestMapper.toCommand(request)
            val payment = createPaymentUseCase.create(command)
            return PaymentRequestMapper.toResponse(payment)
        } catch (ex: Exception) {
            logger.error("Exception in createPayment", ex)
            throw ex
        }
    }

}