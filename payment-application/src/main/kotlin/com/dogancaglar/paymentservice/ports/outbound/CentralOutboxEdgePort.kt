package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent

import java.time.Instant

interface CentralOutboxEdgePort {
    fun insertBatch(edgeNodeId: String, entries: List<OutboxEvent>)
    fun updateWatermark(edgeNodeId: String, forwardedUpTo: Instant)
    fun deleteWatermark(edgeNodeId: String)
}
