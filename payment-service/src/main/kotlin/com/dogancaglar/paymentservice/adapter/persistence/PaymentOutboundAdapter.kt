package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.mapper.PaymentEntityMapper
import com.dogancaglar.paymentservice.adapter.persistence.repository.PaymentMapper
import com.dogancaglar.paymentservice.domain.internal.model.Payment
import com.dogancaglar.paymentservice.domain.port.PaymentOutboundPort
import org.springframework.stereotype.Repository

@Repository
class PaymentOutboundAdapter(
    private val paymentMapper: PaymentMapper
) : PaymentOutboundPort {
    override fun save(payment: Payment) {
        val entity = PaymentEntityMapper.toEntity(payment)
        paymentMapper.insert(entity)
    }

    override fun getMaxPaymentId(): Long {
        return paymentMapper.getMaxPaymentId() ?: 0
    }

}