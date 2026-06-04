package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.payment.Payment
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.common.db.converter.PaymentEntityMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.PaymentMapper
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import org.springframework.stereotype.Repository
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId

/**
 * PaymentOutboundAdapter
 *
 * Outbound persistence adapter implementing [PaymentRepository] for the
 * Central DB. Lives in payment-consumers because the Payment aggregate is
 * created and mutated exclusively by Central Core consumers
 * (PspResultConsumer, CapturePspPerformedConsumer, etc.).
 *
 * The Edge Cell (payment-service) does NOT write to this repository.
 * The edge only writes PaymentIntent, PaymentOrder, and OutboxEvent.
 */
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
        val entity = requireNotNull(paymentMapper.findById(paymentId.value)) {
            "Payment not found for paymentId=${paymentId.value}"
        }
        return entityMapper.toDomain(entity)
    }

    override fun findByPaymentIntentId(paymentIntentId: PaymentIntentId): Payment? {
        val entity = paymentMapper.findByPaymentIntentId(paymentIntentId.value)
        return entity?.let { entityMapper.toDomain(it) }
    }

    override fun getMaxPaymentId(): PaymentId {
        val paymentIdLong = paymentMapper.getMaxPaymentId() ?: 0L
        return PaymentId(paymentIdLong)
    }

    override fun updatePayment(payment: Payment) {
        paymentMapper.update(entityMapper.toEntity(payment))
    }
}
