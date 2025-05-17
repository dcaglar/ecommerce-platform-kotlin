package com.dogancaglar.paymentservice.adapter.persistence.mapper

import com.dogancaglar.paymentservice.adapter.persistence.PaymentEntity
import com.dogancaglar.paymentservice.adapter.persistence.PaymentOrderEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus

object PaymentOrderEntityMapper {

    fun toEntity(domain: PaymentOrder): PaymentOrderEntity {
        return PaymentOrderEntity(
            paymentOrderId = domain.paymentOrderId,
            sellerId = domain.sellerId,
            amountValue = domain.amount.value,
            amountCurrency = domain.amount.currency,
            status = domain.status.name,
            retryCount = domain.retryCount,
            retryReason = domain.retryReason,
            lastErrorMessage = domain.lastErrorMessage ?: "",
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            payment = PaymentEntity(paymentId = domain.paymentId)
        )
    }

    fun toDomain(entity: PaymentOrderEntity): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = entity.paymentOrderId,
            paymentId = entity.payment.paymentId,
            sellerId = entity.sellerId,
            amount = Amount(
                value = entity.amountValue,
                currency = entity.amountCurrency
            ),
            status = PaymentOrderStatus.valueOf(entity.status),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            retryCount = entity.retryCount,
            retryReason = entity.retryReason,
            lastErrorMessage = entity.lastErrorMessage
        )
    }
}