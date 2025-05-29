package com.dogancaglar.paymentservice.application.event

import com.dogancaglar.common.event.PublicAggregateEvent

interface PaymentEvent : PublicAggregateEvent {
    override val publicId: String get() = publicPaymentId
    val publicPaymentId: String
}