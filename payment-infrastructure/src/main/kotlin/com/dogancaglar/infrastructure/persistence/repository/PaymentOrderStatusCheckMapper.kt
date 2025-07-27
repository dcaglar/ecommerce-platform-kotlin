package com.dogancaglar.infrastructure.persistence.repository

import com.dogancaglar.infrastructure.persistence.entity.PaymentOrderStatusCheckEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDateTime

@Mapper
interface PaymentOrderStatusCheckMapper {
    fun findDue(@Param("now") now: LocalDateTime): List<PaymentOrderStatusCheckEntity>

    fun markAsProcessed(@Param("id") id: Long, @Param("updatedAt") updatedAt: LocalDateTime)

    fun insert(entity: PaymentOrderStatusCheckEntity): Int
}