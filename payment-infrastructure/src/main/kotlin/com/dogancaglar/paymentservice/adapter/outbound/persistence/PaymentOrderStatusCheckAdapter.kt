package com.dogancaglar.paymentservice.adapter.outbound.persistence

import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentOrderStatusCheckEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.PaymentOrderStatusCheckMapper
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatusCheck
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderStatusCheckRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class PaymentOrderStatusCheckAdapter(
    private val mapper: PaymentOrderStatusCheckMapper
) : PaymentOrderStatusCheckRepository {

    override fun save(paymentOrderStatusCheck: PaymentOrderStatusCheck) {
        val entity = PaymentOrderStatusCheckEntityMapper.toEntity(paymentOrderStatusCheck)
        mapper.insert(entity)
    }

    override fun findDueStatusChecks(now: LocalDateTime): List<PaymentOrderStatusCheck> {
        val entityList = mapper.findDue(now)
        return entityList.map { PaymentOrderStatusCheckEntityMapper.toDomain(it) }
    }

    @Transactional
    override fun markAsProcessed(id: Long) {
        mapper.markAsProcessed(id, LocalDateTime.now())
    }
}