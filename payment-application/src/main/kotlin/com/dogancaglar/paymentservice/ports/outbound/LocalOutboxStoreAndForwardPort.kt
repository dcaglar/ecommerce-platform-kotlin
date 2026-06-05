package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent

interface LocalOutboxStoreAndForwardPort {
    fun findEligible(batchSize: Int, workerId: String): List<OutboxEvent>
    fun markDispatched(events: List<OutboxEvent>)
    fun reclaimStuck(olderThanSeconds: Int): Int
    fun unclaimFailed(workerId: String, oeids: List<Long>): Int
}
