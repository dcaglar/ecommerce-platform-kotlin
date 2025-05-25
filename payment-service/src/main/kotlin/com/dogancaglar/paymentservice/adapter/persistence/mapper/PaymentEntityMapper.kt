package com.dogancaglar.paymentservice.adapter.persistence.mapper

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Payment


object PaymentEntityMapper {

    fun toEntity(payment: Payment): PaymentEntity {
        return PaymentEntity(
            paymentId = payment.paymentId,
            publicPaymentId = payment.paymentPublicId
        )
    }

    fun toDomain(entity: PaymentEntity): Payment {
        return Payment(
            paymentId = entity.paymentId, paymentPublicId = entity.publicPaymentId,
            buyerId = entity.buyerId, orderId = entity.orderId,
            totalAmount = Amount(entity.totalAmountValue, entity.totalAmountCurrency),
            status = entity.status,
            createdAt = entity.createdAt,
        )
    }
}