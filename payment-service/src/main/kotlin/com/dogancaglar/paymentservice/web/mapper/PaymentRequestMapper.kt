package com.dogancaglar.paymentservice.web.mapper

import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.web.dto.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PaymentRequestMapper {

    fun toDomain(
        dto: PaymentRequestDTO,
        paymentId: Long,
        publicId: String,
        paymentOrders: List<PaymentOrder>
    ): Payment {
        val now = LocalDateTime.now()
        return Payment(
            paymentId = paymentId,
            paymentPublicId = publicId,
            buyerId = dto.buyerId,
            orderId = dto.orderId,
            totalAmount = AmountMapper.toDomain(dto.totalAmount),
            status = PaymentStatus.INITIATED,
            createdAt = now,
            paymentOrders = paymentOrders
        )
    }


    fun toResponse(domain: Payment): PaymentResponseDTO {
        return PaymentResponseDTO(
            id = domain.paymentPublicId,// or use requireNotNull(domain.id)
            status = domain.status.name,
            buyerId = domain.buyerId,
            orderId = domain.orderId,
            totalAmount = AmountMapper.toDto(domain.totalAmount),
            createdAt = domain.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            paymentOrders = domain.paymentOrders.map { toDto(it) }
        )
    }

    private fun toDto(order: PaymentOrder): PaymentOrderResponseDTO {
        return PaymentOrderResponseDTO(
            sellerId = order.sellerId,
            amount = AmountDto(order.amount.value, CurrencyEnum.valueOf(order.amount.currency))
        )
    }


}