package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder

interface RetryQueuePort<T> {
    fun scheduleRetry(paymentOrder: PaymentOrder,backOffSecond:Long)
    fun pollDueRetries(): List<EventEnvelope<T>>

}