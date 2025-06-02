package com.dogancaglar.paymentservice.web.mapper

import com.dogancaglar.paymentservice.domain.internal.model.Payment
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.web.dto.*
import java.time.format.DateTimeFormatter

object PaymentRequestMapper {


    fun toResponse(domain: Payment): PaymentResponseDTO {
        return PaymentResponseDTO(
            id = domain.publicPaymentId,// or use requireNotNull(domain.id)
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