package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.mapper.PaymentEntityMapper
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.port.PaymentOutBoundPort
import org.springframework.stereotype.Repository

@Repository
class JpaPaymentAdapter(
    private val jpaRepository: SpringDataPaymentJpaRepository
) : PaymentOutBoundPort {


    override fun save(payment: Payment): Payment {
        return PaymentEntityMapper.toDomain(jpaRepository.save(PaymentEntityMapper.toEntity(payment)))
    }

    fun findByPaymentId(id: Long): Payment? {
        return PaymentEntityMapper.toDomain(jpaRepository.findById(id).orElse(null))
    }
}
