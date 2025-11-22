package com.dogancaglar.common.event

import java.time.Instant
import java.time.LocalDateTime


interface Event {
    val eventType: String
    fun deterministicEventId(): String
    val timestamp: Instant   // when this event was produced
}