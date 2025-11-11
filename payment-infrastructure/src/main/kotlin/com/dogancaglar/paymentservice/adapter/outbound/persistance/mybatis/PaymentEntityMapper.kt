package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId


object PaymentEntityMapper {
    fun toEntity(payment: Payment): PaymentEntity =
        PaymentEntity(
            paymentId = payment.paymentId.value,
            buyerId = payment.buyerId.value,
            orderId = payment.orderId.value,
            totalAmountValue = payment.totalAmount.quantity,
            capturedAmountValue = payment.capturedAmount.quantity,
            currency = payment.totalAmount.currency.currencyCode,
            status = payment.status.name,
            idempotencyKey = payment.idempotencyKey,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt
        )

    fun toDomain(entity: PaymentEntity): Payment {
        val total = Amount.of(entity.totalAmountValue, Currency(entity.currency))
        val captured = Amount.of(entity.capturedAmountValue, Currency(entity.currency))
        return Payment.Companion.rehydrate(
            paymentId = PaymentId(entity.paymentId),
            buyerId = BuyerId(entity.buyerId),
            orderId = OrderId(entity.orderId),
            totalAmount = total,
            capturedAmount = captured,
            status = PaymentStatus.valueOf(entity.status),
            idempotencyKey = entity.idempotencyKey,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}