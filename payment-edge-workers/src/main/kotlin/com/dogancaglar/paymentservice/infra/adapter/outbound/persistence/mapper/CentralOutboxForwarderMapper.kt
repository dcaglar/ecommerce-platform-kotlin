package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.OutboxEventEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface CentralOutboxForwarderMapper {
    fun insertBatch(entries: List<OutboxEventEntity>)
}
