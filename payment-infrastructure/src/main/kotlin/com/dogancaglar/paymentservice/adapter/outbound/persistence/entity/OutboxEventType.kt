package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentIntentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated

enum class OutboxEventType(val eventClass: Class<*>) {
    payment_intent_authorized(PaymentIntentAuthorized::class.java),
    payment_authorized(PaymentAuthorized::class.java),
    payment_order_created(PaymentOrderCreated::class.java),
    payment_order_capture_command(PaymentOrderCaptureCommand::class.java);

    companion object {
        fun from(value: String): OutboxEventType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}