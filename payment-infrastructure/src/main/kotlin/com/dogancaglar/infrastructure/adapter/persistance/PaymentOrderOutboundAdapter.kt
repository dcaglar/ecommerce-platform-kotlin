package com.dogancaglar.infrastructure.adapter.persistance

import com.dogancaglar.infrastructure.mapper.PaymentOrderEntityMapper
import com.dogancaglar.infrastructure.persistence.repository.PaymentOrderMapper
import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.vo.PaymentId
import com.dogancaglar.payment.domain.model.vo.PaymentOrderId
import com.dogancaglar.port.PaymentOrderRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentOrderOutboundAdapter(
    private val paymentOrderMapper: PaymentOrderMapper
) : PaymentOrderRepository {
    override fun save(paymentOrder: PaymentOrder) {
        val entity = PaymentOrderEntityMapper.toEntity(paymentOrder)
        paymentOrderMapper.upsert(entity)
    }

    override fun upsertAll(orders: List<PaymentOrder>) {
        val entities = orders.map { PaymentOrderEntityMapper.toEntity(it) }
        entities.forEach { paymentOrderMapper.upsert(it) }
    }

    override fun countByPaymentId(paymentId: PaymentId): Long {
        return paymentOrderMapper.countByPaymentId(paymentId.value)
    }

    override fun countByPaymentIdAndStatusIn(paymentId: PaymentId, statuses: List<String>): Long {
        return paymentOrderMapper.countByPaymentIdAndStatusIn(paymentId.value, statuses)
    }

    override fun existsByPaymentIdAndStatus(paymentId: PaymentId, status: String): Boolean {
        return paymentOrderMapper.existsByPaymentIdAndStatus(paymentId.value, status)
    }

    override fun getMaxPaymentOrderId(): PaymentOrderId {
        val maxPAymentOrderIdLong = paymentOrderMapper.getMaxPaymentOrderId() ?: 0
        return PaymentOrderId(maxPAymentOrderIdLong)
    }
}