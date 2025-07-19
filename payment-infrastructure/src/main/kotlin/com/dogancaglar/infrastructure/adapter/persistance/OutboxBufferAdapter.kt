package com.dogancaglar.infrastructure.adapter.persistance

import com.dogancaglar.infrastructure.mapper.OutboxEventEntityMapper
import com.dogancaglar.infrastructure.persistence.entity.OutboxEventEntity
import com.dogancaglar.infrastructure.persistence.repository.OutboxEventMapper
import com.dogancaglar.payment.application.port.outbound.OutboxEventPort
import com.dogancaglar.payment.domain.events.OutboxEvent
import org.springframework.stereotype.Repository

@Repository
class OutboxBufferAdapter(
    private val outboxEventMapper: OutboxEventMapper
) : OutboxEventPort {
    override fun findByStatus(status: String): List<OutboxEvent> =
        outboxEventMapper.findByStatus(status)
            .map { entity: OutboxEventEntity -> OutboxEventEntityMapper.toDomain(entity) }

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> {
        val entities: List<OutboxEventEntity> = events.map { event -> OutboxEventEntityMapper.toEntity(event) }
        if (entities.isNotEmpty()) {
            outboxEventMapper.batchUpsert(entities)
        }
        return entities.map { entity -> OutboxEventEntityMapper.toDomain(entity) }
    }

    override fun save(event: OutboxEvent): OutboxEvent {
        val entity = OutboxEventEntityMapper.toEntity(event)
        outboxEventMapper.batchUpsert(listOf(entity))
        return OutboxEventEntityMapper.toDomain(entity)
    }

    override fun countByStatus(status: String): Long =
        outboxEventMapper.countByStatus(status)

    override fun findBatchForDispatch(batchSize: Int): List<OutboxEvent> =
        outboxEventMapper.findBatchForDispatch(batchSize)
            .map { entity: OutboxEventEntity -> OutboxEventEntityMapper.toDomain(entity) }

}

