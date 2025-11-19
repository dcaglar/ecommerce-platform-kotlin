package com.dogancaglar.common.event

import java.time.LocalDateTime


interface Event {
    val eventType: String
    fun deterministicEventId(): String
    val timestamp: LocalDateTime   // when this event was produced
}