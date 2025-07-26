package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentMapper
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
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