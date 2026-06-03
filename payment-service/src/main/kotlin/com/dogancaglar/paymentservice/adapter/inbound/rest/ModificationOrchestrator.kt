package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.common.id.PublicIdFactory
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.AuthorizationRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CaptureRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CaptureResponseDTO
import com.dogancaglar.paymentservice.application.validator.PaymentValidator
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.inbound.usecases.AuthorizePaymentIntentUseCase
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentResponseDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.PaymentRequestMapper
import com.dogancaglar.paymentservice.application.command.GetPaymentIntentCommand
import com.dogancaglar.paymentservice.application.command.ProcessPaymentIntentUpdateCommand
import com.dogancaglar.paymentservice.ports.inbound.usecases.CapturePaymentUseCase
import com.dogancaglar.paymentservice.ports.inbound.usecases.CreatePaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.inbound.usecases.GetPaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.inbound.usecases.UpdatePaymentIntentUseCase
import com.stripe.model.Event
import com.stripe.model.PaymentIntent
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ModificationOrchestrator(
    private val capturePaymentUseCase: CapturePaymentUseCase,
    private val idGeneratorPort: IdGeneratorPort
) {
    private val logger = LoggerFactory.getLogger(ModificationOrchestrator::class.java)


    fun capturePayment(publicPaymentIntentId: String, request: CaptureRequestDTO): CaptureResponseDTO {
        logger.info("🔁 ModificationOrchestrator.capturePayment started")
        val captureTxId = idGeneratorPort.nextPaymentId()
        val cmd = PaymentRequestMapper.toCapturePaymentCommand(captureTxId, publicPaymentIntentId, request)
        val paymentIntent = capturePaymentUseCase.capture(cmd)
        return PaymentRequestMapper.toCaptureResponseDto(paymentIntent, captureTxId)
    }


}
