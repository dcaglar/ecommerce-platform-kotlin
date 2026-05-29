package com.dogancaglar.paymentservice.ports.outbound

import java.time.Instant

interface EdgeWatermarkPort {
    fun updateWatermark(edgeNodeId: String, forwardedUpTo: Instant)
    fun computeTSafe(): Instant?
    fun deleteWatermark(edgeNodeId: String)
}
