package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.payment.domain.model.Payment
import com.dogancaglar.payment.domain.model.vo.PaymentId
import com.dogancaglar.payment.domain.port.PaymentRepository
import com.dogancaglar.paymentservice.adapter.persistence.mapper.PaymentEntityMapper
import com.dogancaglar.paymentservice.adapter.persistence.repository.PaymentMapper
import org.springframework.stereotype.Repository

@Repository
class PaymentOutboundAdapter(
    private val paymentMapper: PaymentMapper
) : PaymentRepository {
    override fun save(payment: Payment) {
        val entity = PaymentEntityMapper.toEntity(payment)
        paymentMapper.insert(entity)
    }

    override fun getMaxPaymentId(): PaymentId {
        val paymentIdLong = paymentMapper.getMaxPaymentId() ?: 0
        return PaymentId(paymentIdLong)
    }

}