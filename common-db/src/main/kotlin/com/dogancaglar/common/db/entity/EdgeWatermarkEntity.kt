package com.dogancaglar.common.db.entity

import java.time.Instant

data class EdgeWatermarkEntity(
    val edgeNodeId: String,
    val forwardedUpTo: Instant
)
