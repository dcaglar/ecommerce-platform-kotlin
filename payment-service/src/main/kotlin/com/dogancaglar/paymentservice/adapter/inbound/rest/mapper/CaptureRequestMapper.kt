package com.dogancaglar.paymentservice.adapter.inbound.rest.mapper


import com.dogancaglar.common.id.PublicIdFactory
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CaptureRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CaptureResponseDTO
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.commands.CapturePaymentCommand
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import java.time.format.DateTimeFormatter

object CaptureRequestMapper {
    fun toCommand(dto: CaptureRequestDTO): CapturePaymentCommand =
        CapturePaymentCommand(
            paymentId = PaymentId(PublicIdFactory.toInternalId(dto.paymentId)),
            sellerId = SellerId(dto.sellerId),
            captureAmount = AmountMapper.toDomain(dto.amount)
        )

    fun toResponse(domain: PaymentOrder): CaptureResponseDTO {
        return CaptureResponseDTO(
            paymentId = domain.paymentId.toPublicPaymentId(),
            paymentOrderId = domain.paymentOrderId.toPublicPaymentOrderId(),
            status = domain.status.name,
            sellerId = domain.sellerId.value,
            amount = AmountMapper.toDto(domain.amount),
            createdAt = domain.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }
}