package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated

enum class OutboxEventType(val eventClass: Class<*>) {
    PAYMENT_AUTHORIZED(PaymentAuthorized::class.java),
    PAYMENT_ORDER_CREATED(PaymentOrderCreated::class.java),
    PAYMENT_ORDER_CAPTURE_COMMAND(PaymentOrderCaptureCommand::class.java);

    companion object {
        fun from(value: String): OutboxEventType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}