package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentIntentEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class PaymentIntentEntityMapper(
    private val objectMapper: ObjectMapper
) {

    fun toEntity(p: PaymentIntent): PaymentIntentEntity =
        PaymentIntentEntity(
            paymentIntentId = p.paymentIntentId.value,
            buyerId = p.buyerId.value,
            orderId = p.orderId.value,
            totalAmountValue = p.totalAmount.quantity,
            currency = p.totalAmount.currency.currencyCode,
            status = p.status.name,
            createdAt = Utc.toInstant(p.createdAt),
            updatedAt = Utc.toInstant(p.updatedAt),
            paymentLinesJson = objectMapper.writeValueAsString(p.paymentOrderLines)
        )

    fun toDomain(entity: PaymentIntentEntity): PaymentIntent {
        val currency = Currency(entity.currency)
        val total = Amount.of(entity.totalAmountValue, currency)
        val lines: List<PaymentOrderLine> =
            objectMapper.readValue(entity.paymentLinesJson, object : TypeReference<List<PaymentOrderLine>>() {})

        return PaymentIntent.rehydrate(
            paymentIntentId = PaymentIntentId(entity.paymentIntentId),
            buyerId = BuyerId(entity.buyerId),
            orderId = OrderId(entity.orderId),
            totalAmount = total,
            status = PaymentIntentStatus.valueOf(entity.status),
            createdAt = Utc.fromInstant(entity.createdAt),
            updatedAt = Utc.fromInstant(entity.updatedAt),
            paymentOrderLines = lines
        )
    }
}