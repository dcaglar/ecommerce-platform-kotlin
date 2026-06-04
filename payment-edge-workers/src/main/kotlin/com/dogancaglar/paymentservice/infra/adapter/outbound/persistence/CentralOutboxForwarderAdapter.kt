package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.common.time.Utc
import com.dogancaglar.common.db.entity.OutboxEventEntity
import com.dogancaglar.common.db.entity.EdgeWatermarkEntity
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.CentralOutboxForwarderMapper
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxEdgePort
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import org.springframework.stereotype.Repository

import java.time.Instant

@Repository
class CentralOutboxForwarderAdapter(
    private val mapper: CentralOutboxForwarderMapper
) : CentralOutboxEdgePort {

    override fun insertBatch(edgeNodeId: String, entries: List<OutboxEvent>) {
        if (entries.isEmpty()) return
        val entities = entries.map {
            OutboxEventEntity(
                oeid = it.oeid,
                eventType = it.eventType,
                aggregateId = it.aggregateId,
                payload = it.payload,
                status = "NEW",
                createdAt = Utc.toInstant(it.createdAt),
                updatedAt = Utc.toInstant(it.createdAt)
            )
        }
        mapper.insertBatch(entities)
    }

    override fun updateWatermark(edgeNodeId: String, forwardedUpTo: Instant) {
        mapper.upsert(EdgeWatermarkEntity(edgeNodeId, forwardedUpTo))
    }

    override fun deleteWatermark(edgeNodeId: String) {
        mapper.deleteWatermark(edgeNodeId)
    }
}
