package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.ZoneOffset

@Component
class PaymentEntityMapper(
    private val objectMapper: ObjectMapper
) {

    fun toEntity(p: Payment): PaymentEntity =
        PaymentEntity(
            paymentId = p.paymentId.value,
            buyerId = p.buyerId.value,
            orderId = p.orderId.value,
            totalAmountValue = p.totalAmount.quantity,
            currency = p.totalAmount.currency.currencyCode,
            capturedAmountValue = p.capturedAmount.quantity,
            status = p.status.name,
            createdAt = p.createdAt.toInstant(ZoneOffset.UTC),
            updatedAt = p.updatedAt.toInstant(ZoneOffset.UTC),
            paymentLinesJson = objectMapper.writeValueAsString(p.paymentLines)
        )

    fun toDomain(entity: PaymentEntity): Payment {
        val currency = Currency(entity.currency)
        val total = Amount.of(entity.totalAmountValue, currency)
        val captured = if (entity.capturedAmountValue == 0L) {
            Amount.zero(currency)
        } else {
            Amount.of(entity.capturedAmountValue, currency)
        }
        val lines: List<PaymentLine> =
            objectMapper.readValue(entity.paymentLinesJson, object : TypeReference<List<PaymentLine>>() {})

        return Payment.rehydrate(
            paymentId = PaymentId(entity.paymentId),
            buyerId = BuyerId(entity.buyerId),
            orderId = OrderId(entity.orderId),
            totalAmount = total,
            capturedAmount =captured,
            status = PaymentStatus.valueOf(entity.status),
            createdAt = Utc.fromInstant(entity.createdAt),
            updatedAt = Utc.fromInstant(entity.updatedAt),
            paymentLines = lines
        )
    }
}