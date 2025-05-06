package com.dogancaglar.paymentservice.web.mapper

import com.dogancaglar.paymentservice.domain.model.*
import com.dogancaglar.paymentservice.web.dto.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PaymentMapper {

    fun toDomain(dto: PaymentRequestDTO): Payment {
        return Payment(
            id = null,
            buyerId = dto.buyerId,
            orderId = dto.orderId,
            totalAmount = AmountMapper.toDomain(dto.totalAmount),
            status = PaymentStatus.INITIATED,
            createdAt = LocalDateTime.now(),
            paymentOrders = dto.paymentOrders.map {
                PaymentOrder(
                    sellerId = it.sellerId,
                    amount = AmountMapper.toDomain(it.amount)
                )
            }
        )
    }

    fun toResponse(domain: Payment): PaymentResponseDTO {
        return PaymentResponseDTO(
            id = domain.id ?: "",
            status = domain.status.name,
            buyerId = domain.buyerId,
            orderId = domain.orderId,
            totalAmount = AmountMapper.toDto(domain.totalAmount),
            createdAt = domain.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            paymentOrders = domain.paymentOrders.map {
                PaymentOrderResponseDTO(
                    sellerId = it.sellerId,
                    amount = AmountMapper.toDto(it.amount)
                )
            }
        )
    }
}