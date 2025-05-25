package com.dogancaglar.paymentservice.adapter.persistence.mapper

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentOrderEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder

object PaymentOrderEntityMapper {

    fun toEntity(order: PaymentOrder): PaymentOrderEntity {
        return PaymentOrderEntity(
            paymentOrderId = order.paymentOrderId,
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId,
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId,
            amountValue = order.amount.value,
            amountCurrency = order.amount.currency,
            status = order.status,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
            retryCount = order.retryCount,
            retryReason = order.retryReason,
            lastErrorMessage = order.lastErrorMessage
        )
    }

    fun toDomain(entity: PaymentOrderEntity): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = entity.paymentOrderId,
            publicPaymentOrderId = entity.publicPaymentOrderId,
            paymentId = entity.paymentId,
            publicPaymentId = entity.publicPaymentId,
            sellerId = entity.sellerId,
            amount = Amount(entity.amountValue, entity.amountCurrency),
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            retryCount = entity.retryCount,
            retryReason = entity.retryReason,
            lastErrorMessage = entity.lastErrorMessage
        )
    }
}