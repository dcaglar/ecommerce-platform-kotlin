package com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata

object EVENT_TYPE {

    const val PAYMENT_INTENT_AUTHORIZED = "payment_intent_authorized"
    const val PAYMENT_AUTHORIZED = "payment_authorized"


    const val PAYMENT_ORDER_FINALIZED = "payment_order_finalized"
    const val PAYMENT_ORDER_CREATED = "payment_order_created"
    const val PAYMENT_ORDER_CAPTURE_REQUESTED = "payment_order_capture_requested"
    const val PAYMENT_ORDER_PSP_RESULT_UPDATED = "payment_order_psp_result_updated"

    // 🆕 new ones for the accounting flow
    const val LEDGER_RECORDING_REQUESTED = "ledger_recording_requested"
    const val LEDGER_ENTRIES_RECORDED = "ledger_entries_recorded"


}


object Topics {
    const val PAYMENT_AUTHORIZED = "payment_authorized_topic"
    const val PAYMENT_INTENT_AUTHORIZED = "payment_intent_authorized_topic"
    const val PAYMENT_ORDER_CREATED = "payment_order_created_topic"
    const val PAYMENT_ORDER_FINALIZED = "payment_order_finalized_topic"

    // NEW: PSP work queue
    const val PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE = "payment_order_capture_request_queue_topic"
    const val PAYMENT_ORDER_PSP_RESULT_UPDATED = "payment_order_psp_result_updated_topic" // NEW

    const val LEDGER_RECORD_REQUEST_QUEUE = "ledger_record_request_queue_topic"
    const val LEDGER_ENTRIES_RECORDED = "ledger_entries_recorded_topic"

    fun dlqOf(topic: String) = "$topic.DLQ"

    val ALL = listOf(
        PAYMENT_INTENT_AUTHORIZED,
        PAYMENT_AUTHORIZED,
        PAYMENT_ORDER_CREATED,
        PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE,
        PAYMENT_ORDER_PSP_RESULT_UPDATED,
        PAYMENT_ORDER_FINALIZED,
        LEDGER_RECORD_REQUEST_QUEUE,
        LEDGER_ENTRIES_RECORDED
    )
}

object CONSUMER_GROUPS {
    const val PAYMENT_AUTHORIZED_CONSUMER = "payment-authorized-processor-consumer-group"
    const val PAYMENT_INTENT_AUTHORIZED_CONSUMER = "payment-intent-authorized-processor-consumer-group"
    const val PAYMENT_ORDER_ENQUEUER = "payment-order-enqueuer-consumer-group"
    const val PAYMENT_ORDER_CAPTURE_EXECUTOR = "payment-order-capture-executor-consumer-group"
    const val PAYMENT_ORDER_PSP_RESULT_UPDATED = "payment-order-psp-result-updated-consumer-group"
    const val LEDGER_RECORDING_REQUEST_DISPATCHER = "ledger-recording-request-dispatcher-consumer-group"
    const val LEDGER_RECORDING_CONSUMER = "ledger-recording-consumer-group"
    const val ACCOUNT_BALANCE_CONSUMER = "account-balance-consumer-group"

}