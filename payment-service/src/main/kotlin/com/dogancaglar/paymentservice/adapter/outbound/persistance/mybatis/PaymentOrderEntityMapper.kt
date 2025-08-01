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
}