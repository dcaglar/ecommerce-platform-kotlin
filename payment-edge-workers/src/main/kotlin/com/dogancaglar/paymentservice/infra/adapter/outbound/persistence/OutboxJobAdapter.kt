package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.OutboxEventDispatcherMapper
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxEdgePort
import org.springframework.stereotype.Repository

@Repository("outboxJobAdapter")
class OutboxJobAdapter(
    private val outboxEventDispatcherMapper: OutboxEventDispatcherMapper
) : LocalOutboxEdgePort {

    override fun findEligible(batchSize: Int, workerId: String): List<OutboxEvent> =
        outboxEventDispatcherMapper.findBatchForDispatch(batchSize, workerId)
            .map(OutboxEventEntityMapper::toDomain)

    override fun markDispatched(events: List<OutboxEvent>) {
        if (events.isNotEmpty()) {
            outboxEventDispatcherMapper.batchUpdate(events.map(OutboxEventEntityMapper::toEntity))
        }
    }

    override fun reclaimStuck(olderThanSeconds: Int): Int =
        outboxEventDispatcherMapper.reclaimStuckClaims(olderThanSeconds)

    override fun unclaimFailed(workerId: String, oeids: List<Long>): Int {
        if (oeids.isEmpty()) return 0
        val params = mapOf(
            "workerId" to workerId,
            "oeids" to oeids
        )
        return outboxEventDispatcherMapper.unclaimSpecific(params)
    }
}
