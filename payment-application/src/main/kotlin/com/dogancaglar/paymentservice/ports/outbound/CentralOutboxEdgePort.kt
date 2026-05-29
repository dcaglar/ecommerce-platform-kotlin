package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent

interface CentralOutboxEdgePort {
    fun insertBatch(edgeNodeId: String, entries: List<OutboxEvent>)
}
