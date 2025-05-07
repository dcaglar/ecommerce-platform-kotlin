package com.dogancaglar.paymentservice.adapter.persistance.mapper

import com.dogancaglar.paymentservice.adapter.persistance.PaymentEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentRequestDto
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import java.util.UUID


object PaymentEntityMapper {

    fun toEntity(payment: Payment): PaymentEntity {
        return PaymentEntity(
            paymentId = payment.id ?: "",
            buyerId = payment.buyerId,
            orderId = payment.orderId,
            totalAmountValue = payment.totalAmount.value,
            totalAmountCurrency = payment.totalAmount.currency,
            status = payment.status,
            createdAt = payment.createdAt
        )
    }

    fun toDomain(entity: PaymentEntity): Payment {
        return Payment(
            id = entity.paymentId,
            buyerId = entity.buyerId,
            orderId = entity.orderId,
            totalAmount = Amount(entity.totalAmountValue,entity.totalAmountCurrency),
            status = entity.status,
            createdAt = entity.createdAt,
            paymentOrders = emptyList() // PaymentOrders loaded separately
        )
    }
}