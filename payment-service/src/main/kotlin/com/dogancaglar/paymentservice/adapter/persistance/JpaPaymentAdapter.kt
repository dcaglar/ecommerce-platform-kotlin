package com.dogancaglar.paymentservice.adapter.persistance

import com.dogancaglar.paymentservice.adapter.persistance.mapper.PaymentEntityMapper
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.port.PaymentRepository
import org.springframework.stereotype.Repository

@Repository
class JpaPaymentAdapter(
    private val jpaRepository: SpringPaymentJpaRepository
) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        val entity = PaymentEntityMapper.toEntity(payment)
        return PaymentEntityMapper.toDomain(jpaRepository.save(entity))
    }

    override fun findById(id: String): Payment? {
        return PaymentEntityMapper.toDomain(jpaRepository.findById(id).orElse(null))
    }
}
