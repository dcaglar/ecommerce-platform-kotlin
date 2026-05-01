package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.OutboxEventWebMapper
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventRepository
import org.springframework.stereotype.Repository

@Repository
class OutboxWebAdapter(
    private val outboxEventWebMapper: OutboxEventWebMapper
) : OutboxEventRepository {


    override fun save(event: OutboxEvent): OutboxEvent {
        outboxEventWebMapper.insertOutboxEvent(OutboxEventEntityMapper.toEntity(event))
        return event
    }

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> {
        if (events.isNotEmpty())
            outboxEventWebMapper.insertAllOutboxEvents(events.map(OutboxEventEntityMapper::toEntity))
        return events
    }

    override fun findByStatus(status: String): List<OutboxEvent> = throw UnsupportedOperationException("Use poller repository")
    override fun findBatchForDispatch(batchSize: Int, workerId: String): List<OutboxEvent> = throw UnsupportedOperationException("Use poller repository")
    override fun updateAll(events: List<OutboxEvent>): Unit = throw UnsupportedOperationException("Use poller repository")
    override fun countByStatus(status: String): Long = throw UnsupportedOperationException("Use poller repository")
    override fun reclaimStuckClaims(olderThanSeconds: Int): Int = throw UnsupportedOperationException("Use poller repository")
    override fun unclaimSpecific(workerId: String, oeids: List<Long>): Int = throw UnsupportedOperationException("Use poller repository")
}