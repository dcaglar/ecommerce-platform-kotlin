package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.common.db.entity.OutboxEventEntity
import org.apache.ibatis.annotations.Mapper
import java.time.Instant

import com.dogancaglar.common.db.entity.EdgeWatermarkEntity

@Mapper
interface CentralOutboxRelayMapper {
    fun findEligible(tSafe: Instant, batchSize: Int): List<OutboxEventEntity>
    fun countEligible(tSafe: Instant): Long
    fun markDispatched(oeid: Long)
    
    // Edge Watermark operations (consolidated into outbox mapper)
    fun computeTSafe(): Instant?
    fun deleteWatermark(edgeNodeId: String)
}
