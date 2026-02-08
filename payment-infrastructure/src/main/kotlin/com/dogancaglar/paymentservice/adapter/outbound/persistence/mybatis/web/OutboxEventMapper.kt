package com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.web

import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.OutboxEventEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface OutboxEventMapper {
    fun insertOutboxEvent(event: OutboxEventEntity): Int
    fun insertAllOutboxEvents(events: List<OutboxEventEntity>): Int
}