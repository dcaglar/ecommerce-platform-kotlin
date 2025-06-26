package com.dogancaglar.paymentservice.config.messaging

object EVENT_TYPE {
    const val PAYMENT_ORDER_CREATED = "payment_order_retry"
    const val PAYMENT_ORDER_SUCCEEDED = "payment_order_success"
    const val PAYMENT_ORDER_RETRY_REQUESTED = "payment_order_created"
}

object TOPIC_NAMES {
    const val PAYMENT_ORDER_CREATED = "payment_order_created_topic"
    const val PAYMENT_ORDER_RETRY = "payment_order_retry_request_topic"
    const val PAYMENT_STATUS_CHECK_SCHEDULER = "payment_status_check_scheduler_topic"
    const val PAYMENT_ORDER_SUCCEEDED = "payment_order_succeeded_topic"


    const val PAYMENT_ORDER_CREATED_DLQ = "payment_order_created_topic.DLQ"
    const val PAYMENT_ORDER_RETRY_DLQ = "payment_order_retry_request_topic.DLQ"
    const val PAYMENT_STATUS_CHECK_SCHEDULER_DLQ = "payment_status_check_scheduler_topic.DLQ"
    const val PAYMENT_ORDER_SUCCEEDED_DLQ = "payment_order_succeeded_topic.DLQ"


    //Succeeded


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