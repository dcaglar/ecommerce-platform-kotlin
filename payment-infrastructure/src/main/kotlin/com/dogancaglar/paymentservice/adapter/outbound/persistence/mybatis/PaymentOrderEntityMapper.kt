package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentOrderEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import java.time.ZoneOffset

object PaymentOrderEntityMapper {

    /** Domain → Entity */
    fun toEntity(order: PaymentOrder): PaymentOrderEntity =
        PaymentOrderEntity(
            paymentOrderId = order.paymentOrderId.value,
            paymentId = order.paymentId.value,
            sellerId = order.sellerId.value,
            amountValue = order.amount.quantity,
            amountCurrency = order.amount.currency.currencyCode,
            status = order.status,
            createdAt = order.createdAt.toInstant(ZoneOffset.UTC),
            updatedAt = order.updatedAt.toInstant(ZoneOffset.UTC),
            retryCount = order.retryCount
        )

    /** Entity → Domain */
    fun toDomain(entity: PaymentOrderEntity): PaymentOrder =
        PaymentOrder.rehydrate(
            paymentOrderId = PaymentOrderId(entity.paymentOrderId),
            paymentId = PaymentId(entity.paymentId),
            sellerId = SellerId(entity.sellerId),
            amount = Amount.of(entity.amountValue, Currency(entity.amountCurrency)),
            status = entity.status,
            retryCount = entity.retryCount,
            createdAt = entity.createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
            updatedAt = entity.updatedAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        )
    /*
    createdAt = entity.createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
            updatedAt = entity.updatedAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
     */
}