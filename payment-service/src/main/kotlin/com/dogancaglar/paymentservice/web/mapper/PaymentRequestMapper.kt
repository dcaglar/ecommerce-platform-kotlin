package com.dogancaglar.paymentservice.web.mapper

import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import java.time.LocalDateTime

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
            publicId = publicId,
            buyerId = dto.buyerId,
            orderId = dto.orderId,
            totalAmount = AmountMapper.toDomain(dto.totalAmount),
            status = PaymentStatus.INITIATED,
            createdAt = now,
            paymentOrders = paymentOrders
        )
    }


}