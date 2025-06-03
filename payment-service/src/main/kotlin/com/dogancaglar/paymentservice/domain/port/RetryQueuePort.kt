package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import java.util.UUID

interface RetryQueuePort<T> {
    fun scheduleRetry(paymentOrder: PaymentOrder, backOffMillis:Long)
    fun pollDueRetries(): List<EventEnvelope<PaymentOrderRetryRequested>>

}