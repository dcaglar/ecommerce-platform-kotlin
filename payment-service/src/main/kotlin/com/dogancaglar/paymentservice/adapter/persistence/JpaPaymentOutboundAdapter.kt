package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.mapper.PaymentEntityMapper
import com.dogancaglar.paymentservice.adapter.persistence.repository.SpringDataPaymentJpaRepository
import com.dogancaglar.paymentservice.domain.internal.model.Payment
import com.dogancaglar.paymentservice.domain.port.PaymentOutboundPort
import org.springframework.stereotype.Repository

@Repository
class JpaPaymentOutboundAdapter(
    private val jpaRepository: SpringDataPaymentJpaRepository
) : PaymentOutboundPort {

    override fun save(payment: Payment) {
        val entity = PaymentEntityMapper.toEntity(payment)
        jpaRepository.save(entity)
    }


    override fun getMaxPaymentId(): Long {
        return jpaRepository.getMaxPaymentId() ?: 0

    }


}