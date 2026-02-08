package com.dogancaglar.paymentservice.adapter.outbound.persistence

import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentIntentEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.web.PaymentIntentMapper
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentIntentOutboundAdapter(
    private val paymentIntentMapper: PaymentIntentMapper,
    private val entityMapper: PaymentIntentEntityMapper
) : PaymentIntentRepository {




    override fun tryMarkPendingAuth(id: PaymentIntentId, now: java.time.Instant): Boolean {
        return paymentIntentMapper.tryMarkPendingAuth(id.value, now) == 1

    }

    override fun updatePspReference(paymentIntentId: Long, pspReference: String, now: java.time.Instant){
        paymentIntentMapper.updatePspReference(paymentIntentId, pspReference, now)
    }

    override fun save(paymentIntent: PaymentIntent): PaymentIntent {
        paymentIntentMapper.insert(entityMapper.toEntity(paymentIntent))
        return paymentIntent
    }

    override fun findById(paymentIntentId: PaymentIntentId): PaymentIntent {
        return entityMapper.toDomain(paymentIntentMapper.findById(paymentIntentId.value)!!)
    }


    override fun getMaxPaymentIntentId(): PaymentIntentId {
        val paymentIntentIdLong = paymentIntentMapper.getMaxPaymentIntentId() ?: 0
        return PaymentIntentId(paymentIntentIdLong)
    }

    override fun updatePaymentIntent(paymentIntent: PaymentIntent) {
        val entity = entityMapper.toEntity(paymentIntent)
        paymentIntentMapper.update(entity);
    }

}