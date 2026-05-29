package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.EdgeWatermarkEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.EdgeWatermarkMapper
import com.dogancaglar.paymentservice.ports.outbound.EdgeWatermarkPort
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class EdgeWatermarkAdapter(
    private val edgeWatermarkMapper: EdgeWatermarkMapper
) : EdgeWatermarkPort {

    override fun updateWatermark(edgeNodeId: String, forwardedUpTo: Instant) {
        val entity = EdgeWatermarkEntity(
            edgeNodeId = edgeNodeId,
            forwardedUpTo = forwardedUpTo
        )
        edgeWatermarkMapper.upsert(entity)
    }

    override fun computeTSafe(): Instant? {
        return edgeWatermarkMapper.computeTSafe()
    }
    
    override fun deleteWatermark(edgeNodeId: String) {
        edgeWatermarkMapper.deleteWatermark(edgeNodeId)
    }
}
