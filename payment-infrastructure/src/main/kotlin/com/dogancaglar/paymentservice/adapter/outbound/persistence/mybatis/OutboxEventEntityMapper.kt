package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import java.time.ZoneOffset

object OutboxEventEntityMapper {

    fun toDomain(entity: OutboxEventEntity): OutboxEvent =
        OutboxEvent.rehydrate(
            oeid = entity.oeid,
            eventType = entity.eventType,
            aggregateId = entity.aggregateId,
            payload = entity.payload,
            status = entity.status,
            createdAt = entity.createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
            updatedAt = entity.updatedAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        )

    fun toEntity(domain: OutboxEvent): OutboxEventEntity =
        OutboxEventEntity(
            oeid = domain.oeid,
            eventType = domain.eventType,
            aggregateId = domain.aggregateId,
            payload = domain.payload,
            status = domain.status.name,
            createdAt = domain.createdAt.toInstant(ZoneOffset.UTC),
            updatedAt = domain.updatedAt.toInstant(ZoneOffset.UTC),
            claimedAt = null,    // infra-managed
            claimedBy = null     // infra-managed
        )
}