package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity

import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefundReceived
import com.dogancaglar.paymentservice.application.events.PaymentCaptured
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefunded
import com.dogancaglar.paymentservice.application.events.CaptureReceived
import com.dogancaglar.paymentservice.application.events.ExternalAsyncCaptureToPspPerformed

enum class OutboxEventType(val eventClass: Class<*>) {
    payment_authorized(PaymentAuthorized::class.java),
    payment_order_capture_received(PaymentOrderCaptureReceived::class.java),
    payment_order_refund_received(PaymentOrderRefundReceived::class.java),
    payment_order_captured(PaymentCaptured::class.java),
    payment_order_refunded(PaymentOrderRefunded::class.java),
    capture_received(CaptureReceived::class.java),
    external_async_capture_psp_performed(ExternalAsyncCaptureToPspPerformed::class.java);

    //todo implem,ent paymentresultconsumer
    // extend doutboxrelayjob so it can also prtocess new event types,

    companion object {
        fun from(value: String): OutboxEventType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}