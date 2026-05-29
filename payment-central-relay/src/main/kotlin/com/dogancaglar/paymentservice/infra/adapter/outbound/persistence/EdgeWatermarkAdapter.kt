package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.EdgeWatermarkEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.EdgeWatermarkMapper
import com.dogancaglar.paymentservice.ports.outbound.EdgeWatermarkPort
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class EdgeWatermarkAdapter(
    private val mapper: EdgeWatermarkMapper
) : EdgeWatermarkPort {

    override fun updateWatermark(edgeNodeId: String, forwardedUpTo: Instant) {
        val entity = EdgeWatermarkEntity(
            edgeNodeId = edgeNodeId,
            forwardedUpTo = forwardedUpTo
        )
        mapper.upsert(entity)
    }

    override fun computeTSafe(): Instant? {
        return mapper.computeTSafe()
    }

    override fun deleteWatermark(edgeNodeId: String) {
        mapper.deleteWatermark(edgeNodeId)
    }
}
