package com.dogancaglar.paymentservice.adapter.persistence.mapper

import com.dogancaglar.paymentservice.adapter.persistence.OutboxEventEntity
import com.dogancaglar.paymentservice.domain.model.OutboxEvent

object OutboxEventEntityMapper {

    fun toEntity(event: OutboxEvent): OutboxEventEntity {
        return OutboxEventEntity(
            id = event.id,
            eventType = event.eventType,
            aggregateId = event.aggregateId,
            payload = event.payload,
            status = event.status,
            createdAt = event.createdAt
        )
    }

    fun toDomain(entity: OutboxEventEntity): OutboxEvent {
        return OutboxEvent(
            id = entity.id,
            eventType = entity.eventType,
            aggregateId = entity.aggregateId,
            payload = entity.payload,
            status = entity.status,
            createdAt = entity.createdAt
        )
    }
}