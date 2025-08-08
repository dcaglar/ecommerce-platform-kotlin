package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentOrderStatusCheckEntity
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatusCheck

object PaymentOrderStatusCheckEntityMapper {

    fun toDomain(entity: PaymentOrderStatusCheckEntity): PaymentOrderStatusCheck =
        PaymentOrderStatusCheck.reconstructFromPersistence(
            id = entity.id,
            paymentOrderId = entity.paymentOrderId,
            scheduledAt = entity.scheduledAt,
            attempt = entity.attempt,
            status = PaymentOrderStatusCheck.Status.valueOf(entity.status),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    fun toEntity(domain: PaymentOrderStatusCheck): PaymentOrderStatusCheckEntity =
        PaymentOrderStatusCheckEntity(
            id = 0,
            paymentOrderId = domain.paymentOrderId,
            scheduledAt = domain.scheduledAt,
            attempt = domain.attempt,
            status = domain.status.name,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
}