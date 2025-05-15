package com.dogancaglar.paymentservice.adapter.delayqueue

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.*

interface DelayedKafkaMessageRepository : JpaRepository<DelayedKafkaMessageEntity, UUID> {
    fun findAllBySendAfterBefore(now: Instant): List<DelayedKafkaMessageEntity>
}