package com.dogancaglar.paymentservice.adapter.persistance

import com.dogancaglar.paymentservice.adapter.persistance.mapper.PaymentOrderEntityMapper
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.PaymentOrderRepository
import org.springframework.stereotype.Repository

@Repository
class JpaPaymentOrderAdapter(
    private val springRepo: SpringDataPaymentOrderJpaRepository
) : PaymentOrderRepository {

    override fun saveAll(orders: List<PaymentOrder>) {
        val entities = orders.map(PaymentOrderEntityMapper::toEntity)
        springRepo.saveAll(entities)
    }

    override fun findById(id: String): PaymentOrder? {
        return springRepo.findById(id).map(PaymentOrderEntityMapper::toDomain).orElse(null)
    }

    override fun countByPaymentId(paymentId: String): Long =
        springRepo.countByPaymentId(paymentId)

    override fun countByPaymentIdAndStatusIn(paymentId: String, statuses: List<String>): Long =
        springRepo.countByPaymentIdAndStatusIn(paymentId, statuses)

    override fun existsByPaymentIdAndStatus(paymentId: String, status: String): Boolean =
        springRepo.existsByPaymentIdAndStatus(paymentId, status)
}