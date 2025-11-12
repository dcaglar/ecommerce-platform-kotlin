package com.dogancaglar.paymentservice.adapter.outbound.persistence

import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentMapper
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentOutboundAdapter(
    private val paymentMapper: PaymentMapper
) : PaymentRepository {



    override fun saveIdempotent(payment: Payment): Payment {
        paymentMapper.insertIgnore(PaymentEntityMapper.toEntity(payment))
        // ✅ Always read back the persisted version (new or existing)
        return paymentMapper.findByIdempotencyKey(payment.idempotencyKey)!!
            .let { PaymentEntityMapper.toDomain(it) }
    }

    override fun findByIdempotencyKey(key: String): Payment? {
        return paymentMapper.findByIdempotencyKey(key)?.let { PaymentEntityMapper.toDomain(it) }
    }


    override fun getMaxPaymentId(): PaymentId {
        val paymentIdLong = paymentMapper.getMaxPaymentId() ?: 0
        return PaymentId(paymentIdLong)
    }

    override fun updatePayment(payment: Payment): Unit {
        val entity = PaymentEntityMapper.toEntity(payment)
        paymentMapper.update(entity);
    }

}