package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.PaymentRequestMapper
import com.dogancaglar.paymentservice.domain.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.port.out.web.dto.PaymentRequestDTO
import com.dogancaglar.port.out.web.dto.PaymentResponseDTO
import org.springframework.stereotype.Service

@Service
class PaymentService(
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val processPspResultUseCase: ProcessPspResultUseCase
) {
    fun createPayment(request: PaymentRequestDTO): PaymentResponseDTO {
        val command = PaymentRequestMapper.toCommand(request)
        val payment = createPaymentUseCase.create(command)
        return PaymentRequestMapper.toResponse(payment)
    }

    fun processPspResult(event: PaymentOrderEvent, pspStatus: PaymentOrderStatus) {
        processPspResultUseCase.processPspResult(event, pspStatus)
    }


}