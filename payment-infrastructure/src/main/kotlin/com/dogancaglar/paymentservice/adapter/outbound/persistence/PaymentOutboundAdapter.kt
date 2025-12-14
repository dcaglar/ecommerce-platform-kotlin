package com.dogancaglar.paymentservice.adapter.outbound.persistence

import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentMapper
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentOutboundAdapter(
    private val paymentMapper: PaymentMapper,
    private val entityMapper: PaymentEntityMapper
) : PaymentRepository {



    override fun save(payment: Payment): Payment {
        paymentMapper.insert(entityMapper.toEntity(payment))
        return payment
    }

    override fun findById(paymentId: PaymentId): Payment {
        return entityMapper.toDomain(paymentMapper.findById(paymentId.value)!!)
    }


    override fun getMaxPaymentId(): PaymentId {
        val paymentIdLong = paymentMapper.getMaxPaymentId() ?: 0
        return PaymentId(paymentIdLong)
    }

    override fun updatePayment(payment: Payment): Unit {
        val entity = entityMapper.toEntity(payment)
        paymentMapper.update(entity);
    }

}