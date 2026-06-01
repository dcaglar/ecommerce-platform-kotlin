package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity

import java.time.Instant

data class EdgeWatermarkEntity(
    val edgeNodeId: String,
    val forwardedUpTo: Instant
)
