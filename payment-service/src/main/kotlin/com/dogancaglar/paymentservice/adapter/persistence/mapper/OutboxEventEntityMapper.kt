package com.dogancaglar.paymentservice.adapter.persistence.mapper

import com.dogancaglar.paymentservice.adapter.persistence.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.domain.model.OutboxEvent

object OutboxEventEntityMapper {

    fun toEntity(event: OutboxEvent): OutboxEventEntity {
        return OutboxEventEntity(
            eventId = event.eventId,
            eventType = event.eventType,
            aggregateId = event.aggregateId,
            payload = event.payload,
            status = event.getStatus(),
            createdAt = event.createdAt
        )
    }


    fun toDomain(entity: OutboxEventEntity): OutboxEvent {
        return OutboxEvent.restoreFromPersistence(
            eventId = entity.eventId ?: error("eventId cannot be null when restoring from DB"),
            eventType = entity.eventType,
            aggregateId = entity.aggregateId,
            payload = entity.payload,
            status = entity.status,
            createdAt = entity.createdAt
        )
    }


}