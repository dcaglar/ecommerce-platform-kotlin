package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentEntity
import com.dogancaglar.paymentservice.domain.model.Payment


object PaymentEntityMapper {

    fun toEntity(payment: Payment): PaymentEntity {
        return PaymentEntity(
            paymentId = payment.paymentId.value,
            publicPaymentId = payment.publicPaymentId,
            buyerId = payment.buyerId.value,
            orderId = payment.orderId.value,
            amountValue = payment.totalAmount.value,
            amountCurrency = payment.totalAmount.currency.currencyCode,
            status = payment.status,
            createdAt = payment.createdAt
        )
    }
}