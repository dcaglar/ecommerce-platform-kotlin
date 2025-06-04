package com.dogancaglar.paymentservice.domain.port

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder

interface RetryQueuePort<T> {
    fun scheduleRetry(
        paymentOrder: PaymentOrder, backOffMillis: Long, retryReason: String? = "PSP_TIMEOUT",
        lastErrorMessage: String? = "PSP call timed out, retrying"
    )

    fun pollDueRetries(): List<EventEnvelope<T>>
    fun getRetryCount(paymentOrderId: Long): Int
    fun resetRetryCounter(paymentOrderId: Long)


}