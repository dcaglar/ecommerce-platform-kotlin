package com.dogancaglar.paymentservice.adapter.persistance

import com.dogancaglar.paymentservice.adapter.persistance.PaymentEntity
import com.dogancaglar.paymentservice.adapter.persistance.PaymentOrderEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import java.math.BigDecimal

object PaymentOrderEntityMapper {

    fun toEntity(order: PaymentOrder, paymentEntity: PaymentEntity): PaymentOrderEntity {
        return PaymentOrderEntity(
            sellerId = order.sellerId,
            amountValue = order.amount.value,
            amountCurrency = order.amount.currency,
            payment = paymentEntity
        )
    }

    fun toDomain(entity: PaymentOrderEntity): PaymentOrder {
        return PaymentOrder(
            sellerId = entity.sellerId,
            amount = Amount(
                value = entity.amountValue,
                currency = entity.amountCurrency
            )
        )
    }
}