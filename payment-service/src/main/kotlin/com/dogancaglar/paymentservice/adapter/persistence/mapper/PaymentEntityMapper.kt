package com.dogancaglar.paymentservice.adapter.persistence.mapper

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.domain.internal.model.Payment


object PaymentEntityMapper {

    fun toEntity(payment: Payment): PaymentEntity {
        return PaymentEntity(
            paymentId = payment.paymentId,
            publicPaymentId = payment.publicPaymentId,
            buyerId = payment.buyerId,
            orderId = payment.orderId,
            totalAmountValue = payment.totalAmount.value,
            totalAmountCurrency = payment.totalAmount.currency,
            status = payment.status
        )
    }
}