package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.paymentservice.domain.commands.PaymentOrderCaptureCommand


enum class OutboxEventType(val eventClass: Class<*>) {
    PAYMENT_AUTHORIZED(PaymentAuthorized::class.java),
    PAYMENT_ORDER_CREATED(PaymentOrderCreated::class.java),
    PAYMENT_ORDER_CAPTURE_COMMAND(PaymentOrderCaptureCommand::class.java);

    companion object {
        fun from(value: String): OutboxEventType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}