package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.OutboxEventDispatcherMapper
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventRepository
import org.springframework.stereotype.Repository

@Repository("outboxJobAdapter")
class OutboxJobAdapter(
    private val outboxEventDispatcherMapper: OutboxEventDispatcherMapper
) : OutboxEventRepository {

    override fun findByStatus(status: String): List<OutboxEvent> =
        outboxEventDispatcherMapper.findByStatus(status).map { OutboxEventEntityMapper.toDomain(it) }

    override fun save(event: OutboxEvent): OutboxEvent =
        throw UnsupportedOperationException("Poller should not insert")

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> =
        throw UnsupportedOperationException("Poller should not insert")

    override fun findBatchForDispatch(batchSize: Int, workerId: String): List<OutboxEvent> =
        outboxEventDispatcherMapper.findBatchForDispatch(batchSize, workerId)
            .map(OutboxEventEntityMapper::toDomain)

    override fun updateAll(events: List<OutboxEvent>) {
        if (events.isNotEmpty())
            outboxEventDispatcherMapper.batchUpdate(events.map(OutboxEventEntityMapper::toEntity))
    }

    override fun countByStatus(status: String): Long =
        outboxEventDispatcherMapper.countByStatus(status)

    override fun reclaimStuckClaims(olderThanSeconds: Int): Int =
        outboxEventDispatcherMapper.reclaimStuckClaims(olderThanSeconds)

    override fun unclaimSpecific(workerId: String, oeids: List<Long>): Int {
        if (oeids.isEmpty()) return 0
        val params = mapOf(
            "workerId" to workerId,
            "oeids" to oeids
        )
        return outboxEventDispatcherMapper.unclaimSpecific(params)
    }
}
