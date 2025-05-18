package com.dogancaglar.paymentservice.adapter.delayqueue

import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.*

interface ScheduledPaymentOrderRequestRepository : JpaRepository<ScheduledPaymentOrderStatusRequestEntity, UUID> {
    fun findAllBySendAfterBefore(now: Instant): List<ScheduledPaymentOrderStatusRequestEntity>
    fun deleteById(queueId : String)
}