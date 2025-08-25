package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.OutboxEventMapper
import com.dogancaglar.paymentservice.domain.events.OutboxEvent
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventPort
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
            outboxEventMapper.insertAllOutboxEvents(entities)
        }
        return entities.map { entity -> OutboxEventEntityMapper.toDomain(entity) }
    }

    override fun save(event: OutboxEvent): OutboxEvent {
        val entity = OutboxEventEntityMapper.toEntity(event)
        outboxEventMapper.insertOutboxEvent(entity)
        return OutboxEventEntityMapper.toDomain(entity)
    }

    override fun updateAll(events: List<OutboxEvent>) {
        val entities = events.map { OutboxEventEntityMapper.toEntity(it) }
        if (entities.isNotEmpty()) {
            outboxEventMapper.batchUpdate(entities)
        }
    }

    override fun countByStatus(status: String): Long =
        outboxEventMapper.countByStatus(status)

    override fun findBatchForDispatch(batchSize: Int): List<OutboxEvent> =
        outboxEventMapper.findBatchForDispatch(batchSize)
            .map { entity: OutboxEventEntity -> OutboxEventEntityMapper.toDomain(entity) }

}

