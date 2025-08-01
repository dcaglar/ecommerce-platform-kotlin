package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface PaymentMapper {
    // Removed @Select annotation to avoid duplicate mapping with XML
    fun getMaxPaymentId(): Long?

    // Add other CRUD methods as needed, e.g.:
    fun insert(payment: PaymentEntity): Int
    fun findById(id: Long): PaymentEntity?
    fun update(payment: PaymentEntity): Int
    fun deleteById(id: Long): Int
}