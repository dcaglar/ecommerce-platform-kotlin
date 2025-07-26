package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentOrderEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface PaymentOrderMapper {
    // Removed @Select annotation to avoid duplicate mapping with XML
    fun findByPaymentId(@Param("paymentId") paymentId: Long): List<PaymentOrderEntity>

    // Removed @Select annotation to avoid duplicate mapping with XML
    fun countByPaymentId(@Param("paymentId") paymentId: Long): Long

    // Already mapped in XML
    fun countByPaymentIdAndStatusIn(
        @Param("paymentId") paymentId: Long,
        @Param("statuses") statuses: List<String>
    ): Long

    // Removed @Select annotation to avoid duplicate mapping with XML
    fun existsByPaymentIdAndStatus(@Param("paymentId") paymentId: Long, @Param("status") status: String): Boolean

    // Removed @Select annotation to avoid duplicate mapping with XML
    fun getMaxPaymentOrderId(): Long?

    fun insert(paymentOrder: PaymentOrderEntity): Int
    fun upsert(paymentOrder: PaymentOrderEntity): Int
}
