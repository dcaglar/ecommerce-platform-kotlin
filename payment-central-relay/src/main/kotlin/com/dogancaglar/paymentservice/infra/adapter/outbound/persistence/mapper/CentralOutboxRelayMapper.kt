package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.OutboxEventEntity
import org.apache.ibatis.annotations.Mapper
import java.time.Instant

@Mapper
interface CentralOutboxRelayMapper {
    fun findEligible(tSafe: Instant, batchSize: Int): List<OutboxEventEntity>
    fun markDispatched(oeid: Long)
}
