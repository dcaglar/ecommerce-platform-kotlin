package com.dogancaglar.paymentservice.domain.port

interface RetryQueuePort {
    fun scheduleRetry(paymentOrderId: String, retryCount: Int)
    fun pollDueRetries(): List<String>

}