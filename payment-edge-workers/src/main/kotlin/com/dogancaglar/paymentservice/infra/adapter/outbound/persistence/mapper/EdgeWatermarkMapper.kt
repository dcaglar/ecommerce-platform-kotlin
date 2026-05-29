package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.EdgeWatermarkEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface EdgeWatermarkMapper {
    fun upsert(entity: EdgeWatermarkEntity)
    fun computeTSafe(): java.time.Instant?
    fun deleteWatermark(edgeNodeId: String)
}
