package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId

interface RetryQueuePort<T> {
    fun scheduleRetry(
        paymentOrder: PaymentOrder, backOffMillis: Long, retryReason: String? = "PSP_TIMEOUT",
        lastErrorMessage: String? = "PSP call timed out, retrying"
    )

    fun pollDueRetries(maxBatchSize: Long): List<EventEnvelope<T>>
    fun getRetryCount(paymentOrderId: PaymentOrderId): Int
    fun resetRetryCounter(paymentOrderId: PaymentOrderId)


}