package com.dogancaglar.common.event

object EVENT_TYPE {
    const val PAYMENT_ORDER_RETRY_REQUESTED = "payment_order_retry_requested"
    const val PAYMENT_ORDER_STATUS_CHECK_REQUESTED = "payment_order_Status_check_requested"
    const val PAYMENT_ORDER_SUCCEDED = "payment_order_success"
    const val PAYMENT_ORDER_CREATED = "payment_order_created"
}


object Topics {
    const val PAYMENT_ORDER_CREATED = "payment_order_created_topic"
    const val PAYMENT_ORDER_RETRY = "payment_order_retry_request_topic"
    const val PAYMENT_STATUS_CHECK = "payment_status_check_scheduler_topic"
    const val PAYMENT_ORDER_SUCCEEDED = "payment_order_succeeded_topic"

    fun dlqOf(topic: String) = "$topic.DLQ"

    val ALL = listOf(
        PAYMENT_ORDER_CREATED,
        PAYMENT_ORDER_RETRY,
        PAYMENT_STATUS_CHECK,
        PAYMENT_ORDER_SUCCEEDED
    )
}

object CONSUMER_GROUPS {
    const val PAYMENT_ORDER_CREATED = "payment-order-created-consumer-group"
    const val PAYMENT_ORDER_RETRY = "payment-order-retry-consumer-group"
    const val PAYMENT_STATUS_CHECK_SCHEDULER = "payment-status-check-scheduler-consumer-group"
    const val PAYMENT_ORDER_SUCCEEDED = "payment-order-succeeded-consumer-group"

    //DLQ
    const val PAYMENT_ORDER_CREATED_DLQ = "payment-order-created-dlq-consumer-group"
    const val PAYMENT_ORDER_RETRY_DLQ = "payment-order-retry-dlq-consumer-group"
    const val PAYMENT_STATUS_CHECK_SCHEDULER_DLQ = "payment-status-check-scheduler-dlq-consumer-group"
    const val PAYMENT_ORDER_SUCCEEDED_DLQ = "payment-order-succeeded-dlq-consumer-group"
}