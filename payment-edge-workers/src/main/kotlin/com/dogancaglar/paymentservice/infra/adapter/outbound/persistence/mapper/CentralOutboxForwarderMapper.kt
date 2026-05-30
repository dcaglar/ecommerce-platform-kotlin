package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.OutboxEventEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.EdgeWatermarkEntity
import org.apache.ibatis.annotations.Mapper
import java.time.Instant

@Mapper
interface CentralOutboxForwarderMapper {
    fun insertBatch(entries: List<OutboxEventEntity>)
    
    // Edge Watermark operations
    fun upsert(entity: EdgeWatermarkEntity)
    fun computeTSafe(): Instant?
    fun deleteWatermark(edgeNodeId: String)
}
