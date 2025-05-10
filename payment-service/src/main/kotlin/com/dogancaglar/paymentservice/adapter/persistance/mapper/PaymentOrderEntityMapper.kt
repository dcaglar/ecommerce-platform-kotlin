package com.dogancaglar.paymentservice.adapter.persistance.mapper

import com.dogancaglar.paymentservice.adapter.persistance.PaymentEntity
import com.dogancaglar.paymentservice.adapter.persistance.PaymentOrderEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus

object PaymentOrderEntityMapper {

    fun toEntity(domain: PaymentOrder): PaymentOrderEntity {
        return PaymentOrderEntity(
            sellerId = domain.sellerId,
            amountValue = domain.amount.value,
            amountCurrency = domain.amount.currency,
            status = domain.status.name,
            createdAt = domain.createdAt,
            payment =  PaymentEntity(paymentId = domain.paymentId),
            paymentOrderId = domain.paymentOrderId
        )
    }

    fun toDomain(entity: PaymentOrderEntity): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = entity.paymentOrderId ?: throw IllegalStateException("PaymentOrderEntity ID is null"),
            paymentId = entity.payment.paymentId,
            sellerId = entity.sellerId,
            amount = Amount(
                value = entity.amountValue,
                currency = entity.amountCurrency
            ),
            status = PaymentOrderStatus.valueOf(entity.status),
            createdAt = entity.createdAt
        )
    }
}