package com.dogancaglar.paymentservice.adapter.persistence.repository

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentOrderStatusCheckEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDateTime

@Mapper
interface PaymentOrderStatusCheckMapper {
    fun findDue(@Param("now") now: LocalDateTime): List<PaymentOrderStatusCheckEntity>

    fun markAsProcessed(@Param("id") id: Long, @Param("updatedAt") updatedAt: LocalDateTime)

    fun insert(entity: PaymentOrderStatusCheckEntity): Int
}