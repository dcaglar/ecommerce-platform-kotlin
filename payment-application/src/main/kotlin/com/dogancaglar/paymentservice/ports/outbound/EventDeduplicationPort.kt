package com.dogancaglar.paymentservice.ports.outbound


interface EventDeduplicationPort {
    fun exists(eventId: String): Boolean
    fun markProcessed(eventId: String, ttlSeconds: Long)
}