package com.dogancaglar.paymentservice.adapter.delayqueue

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "delayed_kafka_message")
data class DelayedKafkaMessageEntity(
    @Id
    val id: UUID,

    val topic: String,

    val key: String,

    @Column(columnDefinition = "jsonb")
    val payload: String,

    @Column(name = "send_after")
    val sendAfter: Instant,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)