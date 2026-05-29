package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.PaymentTxEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface PaymentTxMapper {
    fun insert(entity: PaymentTxEntity)
    fun findByPaymentId(paymentId: Long): List<PaymentTxEntity>
}
