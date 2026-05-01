package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.serialization.JacksonUtil
import com.fasterxml.jackson.core.type.TypeReference
import org.springframework.stereotype.Component
//todo create like paymentorderentiotymapeper
@Component
class PaymentEntityMapper {
    private val objectMapper = JacksonUtil.createObjectMapper()

    fun toDomain(entity: PaymentEntity): Payment {
        val lines: List<PaymentOrderLine> = objectMapper.readValue(
            entity.paymentLinesJson,
            object : TypeReference<List<PaymentOrderLine>>() {}
        )
        return Payment.rehydrate(
            paymentId = PaymentId(entity.paymentId),
            paymentIntentId = PaymentIntentId(entity.paymentIntentId),
            buyerId = BuyerId(entity.buyerId),
            orderId = OrderId(entity.orderId),
            totalAmount = Amount.of(entity.totalAmountValue, Currency(entity.currency)),
            capturedAmount = Amount.of(entity.capturedAmountValue, Currency(entity.currency)),
            refundedAmount = Amount.of(entity.refundedAmountValue, Currency(entity.currency)),
            status = PaymentStatus.valueOf(entity.status),
            paymentOrderLines = lines,
            createdAt = Utc.fromInstant(entity.createdAt),
            updatedAt = Utc.fromInstant(entity.updatedAt)
        )
    }

    fun toEntity(domain: Payment): PaymentEntity {
        return PaymentEntity(
            paymentId = domain.paymentId.value,
            paymentIntentId = domain.paymentIntentId.value,
            buyerId = domain.buyerId.value,
            orderId = domain.orderId.value,
            totalAmountValue = domain.totalAmount.quantity,
            currency = domain.totalAmount.currency.currencyCode,
            capturedAmountValue = domain.capturedAmount.quantity,
            refundedAmountValue = domain.refundedAmount.quantity,
            status = domain.status.name,
            createdAt = Utc.toInstant(domain.createdAt),
            updatedAt = Utc.toInstant(domain.updatedAt),
            paymentLinesJson = objectMapper.writeValueAsString(domain.paymentOrderLines)
        )
    }
}
