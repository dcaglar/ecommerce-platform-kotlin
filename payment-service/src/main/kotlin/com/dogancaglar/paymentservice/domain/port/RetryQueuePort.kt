package com.dogancaglar.paymentservice.domain.port

interface RetryQueuePort {
    fun scheduleRetry(paymentOrderId: String, delayMillis: Long)
    fun pollDueRetries(): List<String>

}