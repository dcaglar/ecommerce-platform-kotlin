package com.dogancaglar.common.event

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class EventEnvelope<T : Event> @JsonCreator constructor(
    @JsonProperty("eventId")
    val eventId: String,

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
    val parentEventId: String? = null
)