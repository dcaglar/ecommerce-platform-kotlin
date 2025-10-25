package com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.domain.event.OutboxEvent

object OutboxEventEntityMapper {

    fun toEntity(event: OutboxEvent): OutboxEventEntity =
        OutboxEventEntity(
            oeid = event.oeid,
            eventType = event.eventType,
            aggregateId = event.aggregateId,
            payload = event.payload,
            status = event.status.name,  // ✅ convert enum to String
            createdAt = event.createdAt
        )

    fun toDomain(entity: OutboxEventEntity): OutboxEvent =
        OutboxEvent.restore(
            oeid = entity.oeid,
            eventType = entity.eventType,
            aggregateId = entity.aggregateId,
            payload = entity.payload,
            status = entity.status, // ✅ back to enum
            createdAt = entity.createdAt
        )
}