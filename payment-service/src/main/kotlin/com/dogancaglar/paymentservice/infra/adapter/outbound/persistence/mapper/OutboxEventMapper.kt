package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.OutboxEventEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface OutboxEventMapper {
    fun insertOutboxEvent(event: OutboxEventEntity ): Int
    fun insertAllOutboxEvents(events: List<OutboxEventEntity>): Int
}
