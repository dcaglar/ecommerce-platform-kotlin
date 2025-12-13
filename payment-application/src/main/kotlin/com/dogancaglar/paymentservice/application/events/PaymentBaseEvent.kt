package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.common.event.Event
import java.time.Instant
import java.time.LocalDateTime

abstract class PaymentBaseEvent : Event {

    abstract val paymentIntentId: String
    abstract val publicPaymentIntentId: String

    abstract val paymentId: String
    abstract val publicPaymentId: String


    abstract val amountValue: Long
    abstract val currency: String

    /**
     * Transport timestamp:
     * when your application intentionally produced this event/command.
     */
    abstract override val timestamp: Instant
    
    // Event interface members - must be implemented by concrete classes
    abstract override val eventType: String
    abstract override fun deterministicEventId(): String
}