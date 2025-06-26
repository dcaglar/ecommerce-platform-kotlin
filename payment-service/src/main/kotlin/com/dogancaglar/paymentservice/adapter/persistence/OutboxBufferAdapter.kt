package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.mapper.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.adapter.persistence.repository.OutboxEventMapper
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import org.springframework.stereotype.Repository

@Repository
class OutboxBufferAdapter(
    private val outboxEventMapper: OutboxEventMapper
) : OutboxEventPort {
    override fun findByStatus(status: String): List<OutboxEvent> =
        outboxEventMapper.findByStatus(status).map { OutboxEventEntityMapper.toDomain(it) }

    override fun findByStatusWithLimit(status: String, limit: Int): List<OutboxEvent> =
        outboxEventMapper.findByStatusWithLimit(status, limit).map { OutboxEventEntityMapper.toDomain(it) }

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> {
        val entities = events.map { OutboxEventEntityMapper.toEntity(it) }
        if (entities.isNotEmpty()) {
            outboxEventMapper.batchUpsert(entities)
        }
        return entities.map { OutboxEventEntityMapper.toDomain(it) }
    }

    override fun save(event: OutboxEvent): OutboxEvent {
        val entity = OutboxEventEntityMapper.toEntity(event)
        outboxEventMapper.batchUpsert(listOf(entity))
        return OutboxEventEntityMapper.toDomain(entity)
    }

    override fun countByStatus(status: String): Long =
        outboxEventMapper.countByStatus(status)

    override fun findBatchForDispatch(batchSize: Int): List<OutboxEvent> =
        outboxEventMapper.findBatchForDispatch(batchSize).map { OutboxEventEntityMapper.toDomain(it) }

    override fun deleteByStatus(status: String): Int =
        outboxEventMapper.deleteByStatus(status)
}