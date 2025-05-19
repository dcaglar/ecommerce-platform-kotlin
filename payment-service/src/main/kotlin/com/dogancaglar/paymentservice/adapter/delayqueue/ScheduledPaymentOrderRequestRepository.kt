package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.*

interface ScheduledPaymentOrderRequestRepository : JpaRepository<ScheduledPaymentOrderStatusRequestEntity, UUID> {
    fun findAllBySendAfterBefore(now: Instant): List<ScheduledPaymentOrderStatusRequestEntity>
    fun deleteById(queueId : String)


    @Query("""
        SELECT r FROM ScheduledPaymentOrderStatusRequestEntity r
        WHERE r.status = :status AND r.sendAfter <= :now
        ORDER BY r.sendAfter ASC
    """)
    fun findDueRequests(
        @Param("status") status: RequestStatus? = RequestStatus.SCHEDULED,
        @Param("now") now: Instant
    ): List<ScheduledPaymentOrderStatusRequestEntity>

    @Modifying
    @Transactional
    @Query("""
        UPDATE ScheduledPaymentOrderStatusRequestEntity r
        SET r.status = :newStatus
        WHERE r.paymentOrderId = :paymentOrderId
    """)
    fun updateStatusByIds(
        @Param("paymentOrderId") paymentOrderId: String,
        @Param("newStatus") newStatus: RequestStatus ?= RequestStatus.EXECUTED
    ): Int
}