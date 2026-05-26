package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PaymentIntentEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.serialization.JacksonUtil
import com.fasterxml.jackson.core.type.TypeReference
import org.springframework.stereotype.Component
//todo change this lioke paymenyordsdrentitymapper
@Component
class PaymentIntentEntityMapper {
    private val objectMapper = JacksonUtil.createObjectMapper()

    fun toDomain(entity: PaymentIntentEntity): PaymentIntent {
        val lines: List<PaymentOrderLine> = objectMapper.readValue(
            entity.paymentLinesJson,
            object : TypeReference<List<PaymentOrderLine>>() {}
        )
        return PaymentIntent.rehydrate(
            paymentIntentId = PaymentIntentId(entity.paymentIntentId),
            pspReference = entity.pspReference,
            buyerId = BuyerId(entity.buyerId),
            orderId = OrderId(entity.orderId),
            totalAmount = Amount.of(entity.totalAmountValue, Currency(entity.currency)),
            paymentOrderLines = lines,
            status = PaymentIntentStatus.valueOf(entity.status),
            createdAt = Utc.fromInstant(entity.createdAt),
            updatedAt = Utc.fromInstant(entity.updatedAt)
        )
    }

    fun toEntity(domain: PaymentIntent): PaymentIntentEntity {
        return PaymentIntentEntity(
            paymentIntentId = domain.paymentIntentId.value,
            pspReference = domain.pspReference,
            buyerId = domain.buyerId.value,
            orderId = domain.orderId.value,
            totalAmountValue = domain.totalAmount.quantity,
            currency = domain.totalAmount.currency.currencyCode,
            status = domain.status.name,
            createdAt = Utc.toInstant(domain.createdAt),
            updatedAt = Utc.toInstant(domain.updatedAt),
            paymentLinesJson = objectMapper.writeValueAsString(domain.paymentOrderLines)
        )
    }
}
