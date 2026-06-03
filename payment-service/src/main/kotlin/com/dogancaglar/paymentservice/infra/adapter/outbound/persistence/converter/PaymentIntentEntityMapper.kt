package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.payment.PaymentSplit
import com.dogancaglar.paymentservice.domain.model.payment.ProcessingModel
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PaymentIntentEntity
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class PaymentIntentEntityMapper(
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) {

    fun toDomain(entity: PaymentIntentEntity): PaymentIntent {
        val splits: List<PaymentSplit> = if (entity.splitsJson.isNotBlank()) {
            objectMapper.readValue(
                entity.splitsJson,
                object : TypeReference<List<PaymentSplit>>() {}
            )
        } else {
            emptyList()
        }
        return PaymentIntent.rehydrate(
            paymentIntentId = PaymentIntentId(entity.paymentIntentId),
            pspReference = entity.pspReference,
            buyerId = BuyerId(entity.buyerId),
            orderId = OrderId(entity.orderId),
            merchantAccountId = entity.merchantAccountId,
            processingModel = ProcessingModel.valueOf(entity.processingModel),
            totalAmount = Amount.of(entity.totalAmountValue, Currency(entity.currency)),
            splits = splits,
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
            merchantAccountId = domain.merchantAccountId,
            processingModel = domain.processingModel.name,
            totalAmountValue = domain.totalAmount.quantity,
            currency = domain.totalAmount.currency.currencyCode,
            status = domain.status.name,
            createdAt = Utc.toInstant(domain.createdAt),
            updatedAt = Utc.toInstant(domain.updatedAt),
            splitsJson = objectMapper.writeValueAsString(domain.splits)
        )
    }
}
