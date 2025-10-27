package com.dogancaglar.common.event

object EVENT_TYPE {
    const val PAYMENT_ORDER_STATUS_CHECK_REQUESTED = "payment_order_status_check_requested"
    const val PAYMENT_ORDER_SUCCEDED = "payment_order_success"
    const val PAYMENT_ORDER_FAILED = "payment_order_failed"
    const val PAYMENT_ORDER_CREATED = "payment_order_created"
    const val PAYMENT_ORDER_PSP_CALL_REQUESTED = "payment_order_psp_call_requested"
    const val PAYMENT_ORDER_PSP_RESULT_UPDATED = "payment_order_psp_result_updated"

    // 🆕 new ones for the accounting flow
    const val LEDGER_RECORDING_REQUESTED = "ledger_recording_requested"
    const val LEDGER_ENTRIES_RECORDED = "ledger_entries_recorded"


}


object Topics {
    const val PAYMENT_ORDER_CREATED = "payment_order_created_topic"
    const val PAYMENT_STATUS_CHECK = "payment_status_check_scheduler_topic"
    const val PAYMENT_ORDER_FINALIZED = "payment_order_finalized_topic"

    // NEW: PSP work queue
    const val PAYMENT_ORDER_PSP_CALL_REQUESTED = "payment_order_psp_call_requested_topic"
    const val PAYMENT_ORDER_PSP_RESULT_UPDATED = "payment_order_psp_result_updated_topic" // NEW

    const val LEDGER_RECORD_REQUEST_QUEUE = "ledger_record_request_queue_topic"
    const val LEDGER_ENTRIES_RECORDED = "ledger_entries_recorded_topic"

    fun dlqOf(topic: String) = "$topic.DLQ"

    val ALL = listOf(
        PAYMENT_ORDER_CREATED,
        PAYMENT_STATUS_CHECK,
        PAYMENT_ORDER_PSP_CALL_REQUESTED,
        PAYMENT_ORDER_PSP_RESULT_UPDATED,
        PAYMENT_ORDER_FINALIZED,
        LEDGER_RECORD_REQUEST_QUEUE,
        LEDGER_ENTRIES_RECORDED
    )
}

object CONSUMER_GROUPS {
    const val PAYMENT_ORDER_ENQUEUER = "payment-order-enqueuer-consumer-group"
    const val PAYMENT_ORDER_PSP_CALL_EXECUTOR = "payment-order-psp-call-executor-consumer-group"
    const val PAYMENT_STATUS_CHECK_SCHEDULER = "payment-status-check-scheduler-consumer-group"
    const val PAYMENT_ORDER_PSP_RESULT_UPDATED = "payment-order-psp-result-updated-consumer-group"
    const val LEDGER_RECORDING_REQUEST_DISPATCHER = "ledger-recording-request-dispatcher-consumer-group"
    const val LEDGER_RECORDING_CONSUMER = "ledger-recording-consumer-group"
    const val ACCOUNT_BALANCE_CONSUMER = "account-balance-consumer-group"

}