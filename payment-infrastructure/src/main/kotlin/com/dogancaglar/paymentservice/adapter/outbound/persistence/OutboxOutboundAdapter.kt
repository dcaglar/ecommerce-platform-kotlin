package com.dogancaglar.paymentservice.adapter.outbound.persistence

import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.OutboxEventMapper
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventRepository
import org.springframework.stereotype.Repository

@Repository
class OutboxOutboundAdapter(
    private val mapper: OutboxEventMapper
) : OutboxEventRepository {


    override fun findByStatus(status: String): List<OutboxEvent> =
        mapper.findByStatus(status).map { OutboxEventEntityMapper.toDomain(it) }

    override fun save(event: OutboxEvent): OutboxEvent {
        mapper.insertOutboxEvent(OutboxEventEntityMapper.toEntity(event))
        return event
    }

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> {
        if (events.isNotEmpty())
            mapper.insertAllOutboxEvents(events.map(OutboxEventEntityMapper::toEntity))
        return events
    }

    override fun findBatchForDispatch(batchSize: Int, workerId: String): List<OutboxEvent> =
        mapper.findBatchForDispatch(batchSize, workerId)
            .map(OutboxEventEntityMapper::toDomain)

    override fun updateAll(events: List<OutboxEvent>) {
        if (events.isNotEmpty())
            mapper.batchUpdate(events.map(OutboxEventEntityMapper::toEntity))
    }

    override fun countByStatus(status: String): Long =
        mapper.countByStatus(status)

    override fun reclaimStuckClaims(olderThanSeconds: Int): Int =
        mapper.reclaimStuckClaims(olderThanSeconds)

     override  fun unclaimSpecific(workerId: String, oeids: List<Long>): Int  {
        if (oeids.isEmpty()) return 0
        val params = mapOf(
            "workerId" to workerId,
            "oeids" to oeids
        )
        return mapper.unclaimSpecific(params)
    }
}