package com.dogancaglar.paymentservice.adapter.persistence.repository

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentOrderEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SpringDataPaymentOrderJpaRepository : JpaRepository<PaymentOrderEntity, Long> {

    fun findByPaymentId(paymentId: Long): List<PaymentOrderEntity>

    fun countByPaymentId(paymentId: Long): Long

    @Query("SELECT COUNT(po) FROM PaymentOrderEntity po WHERE po.paymentId = :paymentId AND po.status IN :statuses")
    fun countByPaymentIdAndStatusIn(paymentId: Long, statuses: List<String>): Long

    fun existsByPaymentIdAndStatus(paymentId: Long, status: String): Boolean

    @Query("SELECT MAX(p.paymentOrderId) FROM PaymentOrderEntity p")
    fun getMaxPaymentOrderId(): Long?
}
