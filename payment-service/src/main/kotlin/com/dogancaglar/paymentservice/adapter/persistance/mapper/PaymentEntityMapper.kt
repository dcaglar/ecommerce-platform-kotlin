package com.dogancaglar.paymentservice.adapter.persistance.mapper

import com.dogancaglar.paymentservice.adapter.persistance.PaymentEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import java.util.UUID

object PaymentEntityMapper {

    fun toEntity(domain: Payment): PaymentEntity {
        return PaymentEntity(
            paymentId = (domain.id ?: UUID.randomUUID().toString()) as String,            totalAmountValue = domain.totalAmount.value,
            totalAmountCurrency = domain.totalAmount.currency,
            orderId = domain.orderId,
            status = PaymentStatus.INITIATED,
            buyerId = domain.buyerId
        )
    }

    fun toDomain(entity: PaymentEntity): Payment {
        return Payment(
            id = entity.paymentId,
            buyerId = entity.buyerId,
            orderId = entity.orderId,
            totalAmount = Amount(
                value = entity.totalAmountValue,
                currency = entity.totalAmountCurrency
            ),
            status = entity.status,
            createdAt = entity.createdAt,
            paymentOrders = emptyList() // To be populated when order data is available
        )
    }
}