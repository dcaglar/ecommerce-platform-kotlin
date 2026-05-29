package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity

import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefundReceived

enum class OutboxEventType(val eventClass: Class<*>) {
    payment_authorized(PaymentAuthorized::class.java),
    payment_order_capture_received(PaymentOrderCaptureReceived::class.java),
    payment_order_refund_received(PaymentOrderRefundReceived::class.java);

    companion object {
        fun from(value: String): OutboxEventType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}