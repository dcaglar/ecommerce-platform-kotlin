package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.dto.PaymentSplitDto
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.payment.Payment
import com.dogancaglar.paymentservice.domain.model.payment.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.payment.ProcessingModel
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PaymentEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.serialization.JacksonUtil
import com.fasterxml.jackson.core.type.TypeReference
import org.springframework.stereotype.Component

/**
 * PaymentEntityMapper
 *
 * The exclusive bridge between the [Payment] domain aggregate and the
 * flat [PaymentEntity] DB POJO. Lives in payment-consumers because
 * the Payment aggregate is a Central Core concern.
 *
 * Strict separation rules:
 *  - The domain [Payment] has ZERO knowledge of persistence or Jackson.
 *  - The [PaymentEntity] has ZERO knowledge of domain logic.
 *  - This mapper is the ONLY place where conversion between the two occurs.
 *
 * Split serialization:
 *  - [Payment.splits] (List<PaymentSplit>) is serialized to a JSON column
 *    via [PaymentSplitDto] (application-layer DTO carrying Jackson annotations).
 *    The domain [PaymentSplit] remains annotation-free.
 *  - On read, [splitsJson] is deserialized into List<PaymentSplitDto> and
 *    converted back to domain objects via [PaymentSplitDto.toDomain()].
 */
@Component
class PaymentEntityMapper {

    private val objectMapper = JacksonUtil.createObjectMapper()

    private val splitsTypeRef = object : TypeReference<List<PaymentSplitDto>>() {}

    /**
     * toDomain — Converts a flat [PaymentEntity] row into the [Payment] domain aggregate.
     * All type lifting (Long → PaymentId, String → enum, JSON → List<PaymentSplit>) happens here.
     */
    fun toDomain(entity: PaymentEntity): Payment {
        val splits = objectMapper
            .readValue(entity.splitsJson, splitsTypeRef)
            .map { it.toDomain() }

        return Payment.rehydrate(
            paymentId         = PaymentId(entity.paymentId),
            paymentIntentId   = PaymentIntentId(entity.paymentIntentId),
            buyerId           = BuyerId(entity.buyerId),
            merchantAccountId = entity.merchantAccountId,
            processingModel   = ProcessingModel.valueOf(entity.processingModel),
            totalAmount       = Amount.of(entity.totalAmountValue, Currency(entity.currency)),
            capturedAmount    = Amount.of(entity.capturedAmountValue, Currency(entity.currency)),
            refundedAmount    = Amount.of(entity.refundedAmountValue, Currency(entity.currency)),
            status            = PaymentStatus.valueOf(entity.status),
            splits            = splits,
            createdAt         = Utc.fromInstant(entity.createdAt),
            updatedAt         = Utc.fromInstant(entity.updatedAt)
        )
    }

    /**
     * toEntity — Converts a [Payment] domain aggregate into a flat [PaymentEntity] POJO
     * ready for MyBatis INSERT or UPDATE.
     * All type flattening (PaymentId → Long, PaymentStatus → String, List<PaymentSplit> → JSON) happens here.
     */
    fun toEntity(domain: Payment): PaymentEntity {
        val splitsJson = objectMapper.writeValueAsString(
            domain.splits.map { PaymentSplitDto.fromDomain(it) }
        )
        return PaymentEntity(
            paymentId           = domain.paymentId.value,
            paymentIntentId     = domain.paymentIntentId.value,
            buyerId             = domain.buyerId.value,
            merchantAccountId   = domain.merchantAccountId,
            processingModel     = domain.processingModel.name,
            totalAmountValue    = domain.totalAmount.quantity,
            currency            = domain.totalAmount.currency.currencyCode,
            capturedAmountValue = domain.capturedAmount.quantity,
            refundedAmountValue = domain.refundedAmount.quantity,
            status              = domain.status.name,
            splitsJson          = splitsJson,
            createdAt           = Utc.toInstant(domain.createdAt),
            updatedAt           = Utc.toInstant(domain.updatedAt)
        )
    }
}
