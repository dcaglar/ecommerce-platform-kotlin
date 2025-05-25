package com.dogancaglar.paymentservice.adapter.persistence.repository

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentEntity
import org.springframework.data.jpa.repository.JpaRepository


interface SpringDataPaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    fun findByPaymentId(paymentId: Long): PaymentEntity?
}