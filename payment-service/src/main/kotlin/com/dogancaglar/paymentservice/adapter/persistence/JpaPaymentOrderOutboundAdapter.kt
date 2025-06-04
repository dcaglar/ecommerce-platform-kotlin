package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.mapper.PaymentOrderEntityMapper
import com.dogancaglar.paymentservice.adapter.persistence.repository.SpringDataPaymentOrderJpaRepository
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import org.springframework.stereotype.Repository

@Repository
class JpaPaymentOrderOutboundAdapter(
    private val jpaRepository: SpringDataPaymentOrderJpaRepository
) : PaymentOrderOutboundPort {

    override fun save(paymentOrder: PaymentOrder) {
        val entity = PaymentOrderEntityMapper.toEntity(paymentOrder)
        jpaRepository.save(entity)
    }

    override fun saveAll(orders: List<PaymentOrder>) {
        val entities = orders.map { PaymentOrderEntityMapper.toEntity(it) }
        jpaRepository.saveAll(entities)
    }

    override fun countByPaymentId(paymentId: Long): Long {
        return jpaRepository.countByPaymentId(paymentId)
    }

    override fun countByPaymentIdAndStatusIn(paymentId: Long, statuses: List<String>): Long {
        return jpaRepository.countByPaymentIdAndStatusIn(paymentId, statuses)
    }

    override fun existsByPaymentIdAndStatus(paymentId: Long, status: String): Boolean {
        return jpaRepository.existsByPaymentIdAndStatus(paymentId, status)
    }

    override fun getMaxPaymentOrderId(): Long {
        return jpaRepository.getMaxPaymentOrderId() ?: 0
    }
}