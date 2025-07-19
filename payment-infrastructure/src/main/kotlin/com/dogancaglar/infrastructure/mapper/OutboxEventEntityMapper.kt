package com.dogancaglar.infrastructure.mapper

import com.dogancaglar.infrastructure.persistence.entity.OutboxEventEntity
import com.dogancaglar.payment.domain.events.OutboxEvent

object OutboxEventEntityMapper {

    fun toEntity(event: OutboxEvent): OutboxEventEntity {
        return OutboxEventEntity(
            oeid = event.oeid,
            eventType = event.eventType,
            aggregateId = event.aggregateId,
            payload = event.payload,
            status = event.status,
            createdAt = event.createdAt
        )
    }


    fun toDomain(entity: OutboxEventEntity): OutboxEvent {
        return OutboxEvent.restore(
            oeid = entity.oeid,
            eventType = entity.eventType,
            aggregateId = entity.aggregateId,
            payload = entity.payload,
            status = entity.status,
            createdAt = entity.createdAt
        )
    }


}