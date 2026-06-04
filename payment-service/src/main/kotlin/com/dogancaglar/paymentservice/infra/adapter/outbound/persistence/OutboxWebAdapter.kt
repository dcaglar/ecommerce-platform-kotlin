package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.common.db.converter.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.OutboxEventMapper
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxWriterPort
import org.springframework.stereotype.Repository

@Repository("outboxWebAdapter")
class OutboxWebAdapter(
    private val outboxEventMapper: OutboxEventMapper
) : LocalOutboxWriterPort {

    override fun save(event: OutboxEvent): OutboxEvent {
        outboxEventMapper.insertOutboxEvent(OutboxEventEntityMapper.toEntity(event))
        return event
    }

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> {
        if (events.isNotEmpty()) {
            outboxEventMapper.insertAllOutboxEvents(events.map(OutboxEventEntityMapper::toEntity))
        }
        return events
    }
}