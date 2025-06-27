package com.dogancaglar.paymentservice.adapter.persistence.mapper

import com.dogancaglar.payment.domain.model.Payment
import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentEntity


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