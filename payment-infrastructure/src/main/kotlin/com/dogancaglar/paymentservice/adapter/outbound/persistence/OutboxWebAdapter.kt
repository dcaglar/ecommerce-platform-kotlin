package com.dogancaglar.paymentservice.adapter.outbound.persistence

import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.web.OutboxEventMapper
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventRepository
import org.springframework.stereotype.Repository

@Repository
class OutboxWebAdapter(
    private val mapper: OutboxEventMapper
) : OutboxEventRepository {


    override fun save(event: OutboxEvent): OutboxEvent {
        mapper.insertOutboxEvent(OutboxEventEntityMapper.toEntity(event))
        return event
    }

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> {
        if (events.isNotEmpty())
            mapper.insertAllOutboxEvents(events.map(OutboxEventEntityMapper::toEntity))
        return events
    }

    override fun findByStatus(status: String): List<OutboxEvent> = throw UnsupportedOperationException("Use poller repository")
    override fun findBatchForDispatch(batchSize: Int, workerId: String): List<OutboxEvent> = throw UnsupportedOperationException("Use poller repository")
    override fun updateAll(events: List<OutboxEvent>): Unit = throw UnsupportedOperationException("Use poller repository")
    override fun countByStatus(status: String): Long = throw UnsupportedOperationException("Use poller repository")
    override fun reclaimStuckClaims(olderThanSeconds: Int): Int = throw UnsupportedOperationException("Use poller repository")
    override fun unclaimSpecific(workerId: String, oeids: List<Long>): Int = throw UnsupportedOperationException("Use poller repository")
}