package com.dogancaglar.paymentservice.config.metrics

object PaymentMetrics {
    const val PAYMENTS_CREATED = "payments_created_total"
    const val PAYMENT_ORDERS_CREATED = "payment_orders_created_total"
    const val PAYMENT_ORDER_SUCCESS = "payment_order_success_total"

    // ...etc
    fun processingDuration() = "payment_order_processing_duration_seconds"
    fun pspResponseTime() = "psp_response_time_seconds"
}