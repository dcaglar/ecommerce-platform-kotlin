package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.model.PaymentOrder

interface RetryQueuePort<T> {
    fun scheduleRetry(paymentOrder: PaymentOrder)
    fun pollDueRetries(): List<EventEnvelope<T>>

}