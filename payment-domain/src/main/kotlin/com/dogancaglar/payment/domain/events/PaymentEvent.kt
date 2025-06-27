package  com.dogancaglar.payment.application.events

import com.dogancaglar.common.event.PublicAggregateEvent

interface PaymentEvent : PublicAggregateEvent {
    override val publicId: String get() = publicPaymentId
    val publicPaymentId: String
}