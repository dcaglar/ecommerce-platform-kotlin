package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.mapper.PaymentOrderEntityMapper
import com.dogancaglar.paymentservice.adapter.persistence.repository.PaymentOrderMapper
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import org.springframework.stereotype.Repository

@Repository
class PaymentOrderOutboundAdapter(
    private val paymentOrderMapper: PaymentOrderMapper
) : PaymentOrderOutboundPort {
    override fun save(paymentOrder: PaymentOrder) {
        val entity = PaymentOrderEntityMapper.toEntity(paymentOrder)
        paymentOrderMapper.upsert(entity)
    }

    override fun saveAll(orders: List<PaymentOrder>) {
        val entities = orders.map { PaymentOrderEntityMapper.toEntity(it) }
        entities.forEach { paymentOrderMapper.upsert(it) }
    }

    override fun countByPaymentId(paymentId: Long): Long {
        return paymentOrderMapper.countByPaymentId(paymentId)
    }

    override fun countByPaymentIdAndStatusIn(paymentId: Long, statuses: List<String>): Long {
        return paymentOrderMapper.countByPaymentIdAndStatusIn(paymentId, statuses)
    }

    override fun existsByPaymentIdAndStatus(paymentId: Long, status: String): Boolean {
        return paymentOrderMapper.existsByPaymentIdAndStatus(paymentId, status)
    }

    override fun getMaxPaymentOrderId(): Long {
        return paymentOrderMapper.getMaxPaymentOrderId() ?: 0
    }
}