package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.mapper.PaymentOrderEntityMapper
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import org.springframework.stereotype.Repository

@Repository
class JpaPaymentOrderAdapter(
    private val springRepo: SpringDataPaymentOrderJpaRepository
) : PaymentOrderOutboundPort {
    override fun save(paymentOrder: PaymentOrder) {
        springRepo.save(PaymentOrderEntityMapper.toEntity(paymentOrder))
    }

    override fun saveAll(orders: List<PaymentOrder>) {
        val entities = orders.map(PaymentOrderEntityMapper::toEntity)
        springRepo.saveAll(entities)
    }

    override fun findByPaymentId(paymentId: Long): PaymentOrder? {
        return springRepo.findById(paymentId).map(PaymentOrderEntityMapper::toDomain).orElse(null)
    }

    override fun countByPaymentId(paymentId: Long): Long =
        springRepo.countByPaymentId(paymentId)

    override fun countByPaymentIdAndStatusIn(paymentId: Long, statuses: List<String>): Long =
        springRepo.countByPaymentIdAndStatusIn(paymentId, statuses)

    override fun existsByPaymentIdAndStatus(paymentId: StLongring, status: String): Boolean =
        springRepo.existsByPaymentIdAndStatus(paymentId, status)
}