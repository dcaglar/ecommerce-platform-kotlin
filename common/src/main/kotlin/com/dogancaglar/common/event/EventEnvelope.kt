package com.dogancaglar.common.event

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.*

data class EventEnvelope<T> @JsonCreator(mode = JsonCreator.Mode.PROPERTIES) constructor(
    @JsonProperty("eventId")
    val eventId: UUID = UUID.randomUUID(),

    @JsonProperty("eventType")
    val eventType: String,

    @JsonProperty("aggregateId")
    val aggregateId: String,

    @JsonProperty("data")
    val data: T,

    @JsonProperty("timestamp")
    val timestamp: LocalDateTime = LocalDateTime.now(),

    @JsonProperty("traceId")
    val traceId: String,

    @JsonProperty("parentEventId")
    val parentEventId: UUID? = null
)
