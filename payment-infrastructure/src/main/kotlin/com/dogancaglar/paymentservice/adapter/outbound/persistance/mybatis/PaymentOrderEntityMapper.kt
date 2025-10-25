package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentOrderEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId

object PaymentOrderEntityMapper {

    fun toEntity(order: PaymentOrder): PaymentOrderEntity =
        PaymentOrderEntity(
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

    fun toDomain(entity: PaymentOrderEntity): PaymentOrder =
        PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(entity.paymentOrderId))
            .publicPaymentOrderId(entity.publicPaymentOrderId)
            .paymentId(PaymentId(entity.paymentId))
            .publicPaymentId(entity.publicPaymentId)
            .sellerId(SellerId(entity.sellerId))
            .amount(Amount(entity.amountValue, entity.amountCurrency))
            .status(entity.status)
            .createdAt(entity.createdAt)
            .updatedAt(entity.updatedAt)
            .retryCount(entity.retryCount)
            .retryReason(entity.retryReason)
            .lastErrorMessage(entity.lastErrorMessage)
            .buildFromPersistence()
}