package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentOrderEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentOrderMapper
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentOrderOutboundAdapter(
    private val paymentOrderMapper: PaymentOrderMapper
) : PaymentOrderRepository {


    override fun updateReturningIdempotent(order: PaymentOrder): PaymentOrder? {
        val e = PaymentOrderEntityMapper.toEntity(order)
        val updated = paymentOrderMapper.updateReturningIdempotent(e)
        if (updated != null) return PaymentOrderEntityMapper.toDomain(updated)

        // No row updated → terminal or missing. Re-read to reflect truth if present.
        val found = paymentOrderMapper.findByPaymentOrderId(e.paymentOrderId).firstOrNull()
        return found?.let(PaymentOrderEntityMapper::toDomain)
    }


    override fun insertAll(orders: List<PaymentOrder>): Unit {
        val entities = orders.map { PaymentOrderEntityMapper.toEntity(it) }
        paymentOrderMapper.insertAllIgnore(entities)
    }

    override fun countByPaymentId(paymentId: PaymentId): Long {
        return paymentOrderMapper.countByPaymentId(paymentId.value)
    }

    override fun findByPaymentOrderId(paymentOrderId: PaymentOrderId): List<PaymentOrder> {
        return paymentOrderMapper.findByPaymentOrderId(paymentOrderId.value)
            .map { PaymentOrderEntityMapper.toDomain(it) }
    }

    override fun countByPaymentIdAndStatusIn(paymentId: PaymentId, statuses: List<String>): Long {
        return paymentOrderMapper.countByPaymentIdAndStatusIn(paymentId.value, statuses)
    }

    override fun existsByPaymentIdAndStatus(paymentId: PaymentId, status: String): Boolean {
        return paymentOrderMapper.existsByPaymentIdAndStatus(paymentId.value, status)
    }

    override fun getMaxPaymentOrderId(): PaymentOrderId {
        val maxPaymentOrderIdLong = paymentOrderMapper.getMaxPaymentOrderId() ?: 0
        return PaymentOrderId(maxPaymentOrderIdLong)
    }
}