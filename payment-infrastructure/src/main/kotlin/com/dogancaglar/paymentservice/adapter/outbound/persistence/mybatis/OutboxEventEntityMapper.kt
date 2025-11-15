package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.domain.model.OutboxEvent

object OutboxEventEntityMapper {

    fun toDomain(entity: OutboxEventEntity): OutboxEvent =
        OutboxEvent.rehydrate(
            oeid = entity.oeid,
            eventType = entity.eventType,
            aggregateId = entity.aggregateId,
            payload = entity.payload,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    fun toEntity(domain: OutboxEvent): OutboxEventEntity =
        OutboxEventEntity(
            oeid = domain.oeid,
            eventType = domain.eventType,
            aggregateId = domain.aggregateId,
            payload = domain.payload,
            status = domain.status.name,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            claimedAt = null,    // infra-managed
            claimedBy = null     // infra-managed
        )
}