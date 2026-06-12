package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntent
import com.dogancaglar.common.db.converter.PaymentIntentEntityMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.edge.PaymentIntentMapper
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import org.springframework.stereotype.Repository
import java.time.Instant

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference
import org.springframework.beans.factory.annotation.Qualifier
import com.dogancaglar.paymentservice.domain.model.payment.PaymentSplit

@Repository
class PaymentIntentOutboundAdapter(
    private val paymentIntentMapper: PaymentIntentMapper,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper
) : PaymentIntentRepository {

    private val splitsTypeRef = object : TypeReference<List<PaymentSplit>>() {}

    override fun tryMarkPendingAuth(id: PaymentIntentId, now: Instant): Boolean {
        return paymentIntentMapper.tryMarkPendingAuth(id.value, now) == 1
    }

    override fun updatePspReference(paymentIntentId: Long, pspReference: String, now: Instant){
        paymentIntentMapper.updatePspReference(paymentIntentId, pspReference, now)
    }

    override fun save(paymentIntent: PaymentIntent): PaymentIntent {
        val splitsJson = objectMapper.writeValueAsString(paymentIntent.splits)
        paymentIntentMapper.insert(PaymentIntentEntityMapper.toEntity(paymentIntent, splitsJson))
        return paymentIntent
    }

a        val entity = paymentIntentMapper.findById(paymentIntentId.value)!!
        val splits: List<PaymentSplit> = if (entity.splitsJson.isNotBlank()) {
            objectMapper.readValue(entity.splitsJson, splitsTypeRef)
        } else {
            emptyList()
        }
        return PaymentIntentEntityMapper.toDomain(entity, splits)
    }

    override fun getMaxPaymentIntentId(): PaymentIntentId {
        val paymentIntentIdLong = paymentIntentMapper.getMaxPaymentIntentId() ?: 0
        return PaymentIntentId(paymentIntentIdLong)
    }

    override fun updatePaymentIntent(paymentIntent: PaymentIntent) {
        val splitsJson = objectMapper.writeValueAsString(paymentIntent.splits)
        val entity = PaymentIntentEntityMapper.toEntity(paymentIntent, splitsJson)
        paymentIntentMapper.update(entity)
    }

}
