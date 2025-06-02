package com.dogancaglar.paymentservice.adapter.persistence.mapper

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentOrderEntity
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.Amount

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
}