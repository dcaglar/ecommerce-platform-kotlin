package com.dogancaglar.paymentservice.adapter.persistence.repository

import com.dogancaglar.paymentservice.adapter.persistence.entity.PaymentOrderStatusCheckEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PaymentOrderStatusCheckJpaRepository : JpaRepository<PaymentOrderStatusCheckEntity, Long> {
    @Query("SELECT c FROM PaymentOrderStatusCheckEntity c WHERE c.scheduledAt <= :now AND c.status = 'SCHEDULED'")
    fun findDue(@Param("now") now: LocalDateTime): List<PaymentOrderStatusCheckEntity>

    @Modifying
    @Query("UPDATE PaymentOrderStatusCheckEntity c SET c.status = 'PROCESSED', c.updatedAt = :updatedAt WHERE c.id = :id")
    fun markAsProcessed(@Param("id") id: Long, @Param("updatedAt") updatedAt: LocalDateTime)
}