package com.dogancaglar.infrastructure.mapper

import com.dogancaglar.infrastructure.persistence.entity.PaymentEntity
import com.dogancaglar.payment.domain.model.Payment


object PaymentEntityMapper {

    fun toEntity(payment: Payment): PaymentEntity {
        return PaymentEntity(
            paymentId = payment.paymentId.value,
            publicPaymentId = payment.publicPaymentId,
            buyerId = payment.buyerId.value,
            orderId = payment.orderId.value,
            amountValue = payment.totalAmount.value,
            amountCurrency = payment.totalAmount.currency,
            status = payment.status
        )
    }
}