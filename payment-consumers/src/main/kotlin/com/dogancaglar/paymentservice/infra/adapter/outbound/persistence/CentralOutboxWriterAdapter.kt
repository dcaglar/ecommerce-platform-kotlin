package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.common.db.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.CentralOutboxWriterMapper
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxWriterPort
import org.springframework.stereotype.Component
import java.time.ZoneOffset.UTC

@Component
class CentralOutboxWriterAdapter(
    private val centralOutboxEventMapper: CentralOutboxWriterMapper
) : CentralOutboxWriterPort {

    override fun save(event: OutboxEvent): OutboxEvent {
        val entity = toEntity(event)
        centralOutboxEventMapper.insertOutboxEvent(entity)
        return event
    }

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> {
        if (events.isEmpty()) return emptyList()
        val entities = events.map { toEntity(it) }
        centralOutboxEventMapper.insertAllOutboxEvents(entities)
        return events
    }

    private fun toEntity(event: OutboxEvent): OutboxEventEntity {
        return OutboxEventEntity(
            oeid = event.oeid,
            eventType = event.eventType,
            aggregateId = event.aggregateId,
            payload = event.payload,

            status = event.status.name,
            createdAt = event.createdAt.toInstant(UTC),
            updatedAt = event.updatedAt.toInstant(UTC)
        )
    }
}
