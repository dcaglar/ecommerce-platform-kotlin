package com.dogancaglar.common.event

object EVENT_TYPE {
    const val PAYMENT_ORDER_RETRY = "payment_order_retry"
    const val PAYMENT_ORDER_SUCCESS = "payment_order_success"
    const val PAYMENT_ORDER_CREATED = "payment_order_created"
}

object TOPICS {
    const val PAYMENT_ORDER_CREATED = "payment_order_created_queue"
    const val PAYMENT_ORDER_RETRY = "payment_order_retry_request_topic"
    const val PAYMENT_STATUS_CHECK_SCHEDULER= "payment_status_check_scheduler_topic"
    const val DUE_PAYMENT_STATUS_CHECK =  "due_payment_status_check_topic"
    const val PAYMENT_ORDER_SUCCEDED = "payment_order_succeded_topic"

}