package com.dogancaglar.paymentservice.web.mapper

import com.dogancaglar.paymentservice.domain.model.*
import com.dogancaglar.paymentservice.web.dto.PaymentOrderRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentOrderResponseDTO
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentResponseDTO
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.UUID

object PaymentRequestMapper {

    fun toDomain(dto: PaymentRequestDTO): Payment {
        val now = LocalDateTime.now()
        val paymentId = UUID.randomUUID().toString()

        val paymentOrders = dto.paymentOrders.map {
            PaymentOrder(
                paymentOrderId = UUID.randomUUID().toString(),
                paymentId = paymentId,  // ✅ filled immediately
                sellerId = it.sellerId,
                amount = AmountMapper.toDomain(it.amount),
                status = PaymentOrderStatus.INITIATED,
                retryCount = 0,
                createdAt = now
            )
        }

        return Payment(
            id = paymentId,
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
            id = domain.id ?: "", // or use requireNotNull(domain.id)
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
            amount = AmountMapper.toDto(order.amount),
        )
    }

}