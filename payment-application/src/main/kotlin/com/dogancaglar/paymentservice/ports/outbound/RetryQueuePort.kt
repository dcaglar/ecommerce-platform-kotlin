package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.RetryItem
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId

interface RetryQueuePort<T> {
    fun scheduleRetry(
        paymentOrder: PaymentOrder,
        backOffMillis: Long,
    )


    fun getRetryCount(paymentOrderId: PaymentOrderId): Int
    fun resetRetryCounter(paymentOrderId: PaymentOrderId)
    fun pollDueRetriesToInflight(maxBatchSize: Long): List<RetryItem>

}