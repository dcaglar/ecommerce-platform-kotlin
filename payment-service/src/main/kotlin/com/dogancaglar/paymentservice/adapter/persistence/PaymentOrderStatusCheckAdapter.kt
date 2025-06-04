package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentOrderStatusCheckEntity
import com.dogancaglar.paymentservice.adapter.persistence.mapper.PaymentOrderStatusCheckEntityMapper
import com.dogancaglar.paymentservice.adapter.persistence.repository.PaymentOrderStatusCheckJpaRepository
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrderStatusCheck
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderStatusCheckOutBoundPort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class PaymentOrderStatusCheckAdapter(
    private val jpaRepository: PaymentOrderStatusCheckJpaRepository
) : PaymentOrderStatusCheckOutBoundPort {

    override fun save(paymentOrderStatusCheck: PaymentOrderStatusCheck) {
        val paymentOrderStatusCheckEntity = PaymentOrderStatusCheckEntityMapper.toEntity(paymentOrderStatusCheck)
        jpaRepository.save(paymentOrderStatusCheckEntity)
    }
    override fun findDueStatusChecks(now: LocalDateTime): List<PaymentOrderStatusCheck> {
        val paymentOrderStatusCheckEntityList = jpaRepository.findDue(now)
        return paymentOrderStatusCheckEntityList.map { PaymentOrderStatusCheckEntityMapper.toDomain(it) }
    }

    @Transactional
    override fun markAsProcessed(id: Long) {
        jpaRepository.markAsProcessed(id, LocalDateTime.now())
    }
}