package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.CentralOutboxRelayMapper
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxRelayPort
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class CentralOutboxRelayAdapter(
    private val mapper: CentralOutboxRelayMapper
) : CentralOutboxRelayPort {

    override fun findEligible(tSafe: Instant, batchSize: Int): List<OutboxEvent> {
        val entities = mapper.findEligible(tSafe, batchSize)
        return entities.map {
            OutboxEvent.rehydrate(
                oeid = it.oeid,
                eventType = it.eventType,
                aggregateId = it.aggregateId,
                payload = it.payload,
                status = it.status,
                createdAt = Utc.fromInstant(it.createdAt),
                updatedAt = Utc.fromInstant(it.updatedAt)
            )
        }
    }

    override fun markDispatched(oeid: Long) {
        mapper.markDispatched(oeid)
    }

    override fun countEligible(tSafe: Instant): Long {
        return mapper.countEligible(tSafe)
    }

    override fun computeTSafe(): Instant? {
        return mapper.computeTSafe()
    }

    override fun deleteWatermark(edgeNodeId: String) {
        mapper.deleteWatermark(edgeNodeId)
    }
}
