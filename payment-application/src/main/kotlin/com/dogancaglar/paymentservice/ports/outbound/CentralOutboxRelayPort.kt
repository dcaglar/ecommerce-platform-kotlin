package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import java.time.Instant

interface CentralOutboxRelayPort {
    fun findEligible(tSafe: Instant, batchSize: Int): List<OutboxEvent>
    fun countEligible(tSafe: Instant): Long
    fun markDispatched(oeid: Long, createdAt: Instant)
    fun computeTSafe(): Instant?
    fun deleteWatermark(edgeNodeId: String)
}
