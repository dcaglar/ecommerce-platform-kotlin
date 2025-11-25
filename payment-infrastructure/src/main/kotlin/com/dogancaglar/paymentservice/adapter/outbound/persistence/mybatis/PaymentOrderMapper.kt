package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.PaymentOrderEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDateTime

@Mapper
interface PaymentOrderMapper {
    // Removed @Select annotation to avoid duplicate mapping with XML
    fun findByPaymentId(@Param("paymentId") paymentId: Long): List<PaymentOrderEntity>
    fun findByPaymentOrderId(@Param("paymentOrderId") paymentOrderId: Long): List<PaymentOrderEntity>

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


    fun insertAllIgnore(list: List<PaymentOrderEntity>): Int              // new: bulk create simple

    fun insert(paymentOrder: PaymentOrderEntity): Int

    fun updateReturningIdempotent(paymentOrder: PaymentOrderEntity): PaymentOrderEntity?

    fun updateReturningIdempotentInitialCaptureRequest(paymentOrderId: Long,updateAt: LocalDateTime): PaymentOrderEntity?


    fun deleteAll(): Int
    fun countAll(): Int
}

