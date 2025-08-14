package com.dogancaglar.common.event

object EVENT_TYPE {
    const val PAYMENT_ORDER_STATUS_CHECK_REQUESTED = "payment_order_status_check_requested"
    const val PAYMENT_ORDER_SUCCEDED = "payment_order_success"
    const val PAYMENT_ORDER_CREATED = "payment_order_created"
    const val PAYMENT_ORDER_PSP_CALL_REQUESTED = "payment_order_psp_call_requested"
}


object Topics {
    const val PAYMENT_ORDER_CREATED = "payment_order_created_topic"
    const val PAYMENT_STATUS_CHECK = "payment_status_check_scheduler_topic"
    const val PAYMENT_ORDER_SUCCEEDED = "payment_order_succeeded_topic"

    // NEW: PSP work queue
    const val PAYMENT_ORDER_PSP_CALL_REQUESTED = "payment_order_psp_call_requested_topic"

    fun dlqOf(topic: String) = "$topic.DLQ"

    val ALL = listOf(
        PAYMENT_ORDER_CREATED,
        PAYMENT_STATUS_CHECK,
        PAYMENT_ORDER_SUCCEEDED,
        PAYMENT_ORDER_PSP_CALL_REQUESTED
    )
}

object CONSUMER_GROUPS {
    const val PAYMENT_ORDER_ENQUEUER = "payment-order-enqueuer-consumer-group"
    const val PAYMENT_ORDER_PSP_CALL_EXECUTOR = "payment-order-psp-call-executor-consumer-group"
    const val PAYMENT_STATUS_CHECK_SCHEDULER = "payment-status-check-scheduler-consumer-group"
    const val PAYMENT_ORDER_SUCCEEDED = "payment-order-succeeded-consumer-group"


}