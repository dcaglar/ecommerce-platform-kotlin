package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentOrderEntity
import com.dogancaglar.paymentservice.domain.model.PaymentOrder

object PaymentOrderEntityMapper {

    fun toEntity(order: PaymentOrder): PaymentOrderEntity {
        return PaymentOrderEntity(
            paymentOrderId = order.paymentOrderId.value,
            publicPaymentOrderId = order.publicPaymentOrderId,
            paymentId = order.paymentId.value,
            publicPaymentId = order.publicPaymentId,
            sellerId = order.sellerId.value,
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
            paymentOrderId = com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId(entity.paymentOrderId),
            publicPaymentOrderId = entity.publicPaymentOrderId,
            paymentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentId(entity.paymentId),
            publicPaymentId = entity.publicPaymentId,
            sellerId = com.dogancaglar.paymentservice.domain.model.vo.SellerId(entity.sellerId),
            amount = com.dogancaglar.paymentservice.domain.model.Amount(
                value = entity.amountValue,
                currency = entity.amountCurrency
            ),
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            retryCount = entity.retryCount,
            retryReason = entity.retryReason,
            lastErrorMessage = entity.lastErrorMessage
        )
    }
}