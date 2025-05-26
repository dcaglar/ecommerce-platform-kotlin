package com.dogancaglar.paymentservice.adapter.persistence.repository

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query


interface SpringDataPaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    fun findByPaymentId(paymentId: Long): PaymentEntity?

    @Query("SELECT MAX(p.paymentId) FROM PaymentEntity p")
    fun getMaxPaymentId(): Long?
}