package com.dogancaglar.paymentservice.application.events

import com.dogancaglar.common.event.Event
import java.time.LocalDateTime

abstract class PaymentOrderBaseEvent : Event {

    abstract val paymentOrderId: String
    abstract val publicPaymentOrderId: String

    abstract val paymentId: String
    abstract val publicPaymentId: String

    abstract val sellerId: String

    abstract val amountValue: Long
    abstract val currency: String

    /**
     * Transport timestamp:
     * when your application intentionally produced this event/command.
     */
    abstract override val timestamp: LocalDateTime
    
    // Event interface members - must be implemented by concrete classes
    abstract override val eventType: String
    abstract override fun deterministicEventId(): String
}