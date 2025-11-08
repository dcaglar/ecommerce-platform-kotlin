package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.PaymentRequestMapper
import com.dogancaglar.paymentservice.application.validator.PaymentValidator
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import com.dogancaglar.port.out.web.dto.PaymentRequestDTO
import com.dogancaglar.port.out.web.dto.PaymentResponseDTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val paymentValidator: PaymentValidator
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    @Transactional(transactionManager = "webTxManager", timeout = 2)
    fun createPayment(request: PaymentRequestDTO): PaymentResponseDTO {
        try {
            paymentValidator.validate(request)
            val command = PaymentRequestMapper.toCommand(request)
            val payment = createPaymentUseCase.create(command)
            return PaymentRequestMapper.toResponse(payment)
        } catch (ex: Exception) {
            logger.error("Exception in createPayment", ex)
            throw ex
        }
    }

}