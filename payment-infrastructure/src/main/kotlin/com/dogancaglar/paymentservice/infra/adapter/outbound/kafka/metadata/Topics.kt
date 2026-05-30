package com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata

object EVENT_TYPE {

    const val PAYMENT_AUTHORIZED = "payment_authorized"


    const val PAYMENT_ORDER_FINALIZED = "payment_order_finalized"
    const val PAYMENT_ORDER_CREATED = "payment_order_capture_received"
    const val PAYMENT_ORDER_CAPTURE_REQUESTED = "payment_order_capture_requested"
    const val PAYMENT_ORDER_PSP_RESULT_UPDATED = "payment_order_psp_result_updated"

    const val PAYMENT_ORDER_CAPTURED = "payment_order_captured"
    const val PAYMENT_ORDER_REFUNDED = "payment_order_refunded"

    // 🆕 new ones for the accounting flow
    const val LEDGER_RECORDING_REQUESTED = "ledger_recording_requested"
    const val LEDGER_ENTRIES_RECORDED = "ledger_entries_recorded"


}


object Topics {
    const val CAPTURE_QUEUE = "capture_topic"
    const val REFUND_QUEUE = "refund_topic"
    const val PSP_RESULT = "psp_result_topic"
    const val PAYMENT_AUTHORIZED = "payment_authorized_topic"

    const val PAYMENT_ORDER_CREATED = "payment_order_created_topic"
    const val PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE = "payment_order_capture_requested_topic"

    const val PAYMENT_ORDER_PSP_RESULT_UPDATED = "payment_order_psp_result_updated_topic"
    const val PAYMENT_ORDER_FINALIZED = "payment_order_finalized_topic"
    const val PAYMENT_ORDER_CAPTURED_TOPIC = "payment_order_captured_topic"
    const val PAYMENT_ORDER_REFUNDED_TOPIC = "payment_order_refunded_topic"
    const val LEDGER_RECORD_REQUEST_QUEUE = "ledger_record_request_queue_topic"
    const val LEDGER_ENTRIES_RECORDED = "ledger_entries_recorded_topic"

    fun dlqOf(topic: String) = "$topic.DLQ"

    val ALL = listOf(
        CAPTURE_QUEUE,
        REFUND_QUEUE,
        PSP_RESULT,
        PAYMENT_AUTHORIZED,
        PAYMENT_ORDER_CREATED,
        PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE,
        PAYMENT_ORDER_PSP_RESULT_UPDATED,
        PAYMENT_ORDER_FINALIZED,
        PAYMENT_ORDER_CAPTURED_TOPIC,
        PAYMENT_ORDER_REFUNDED_TOPIC,
        LEDGER_RECORD_REQUEST_QUEUE,
        LEDGER_ENTRIES_RECORDED
    )
}

object CONSUMER_GROUPS {
    const val PSP_CAPTURE_EXECUTOR = "psp-capture-executor-consumer-group"
    const val PSP_REFUND_EXECUTOR = "psp-refund-executor-consumer-group"
    const val PSP_RESULT_CONSUMER = "psp-result-consumer-group"

    const val PAYMENT_AUTHORIZED_CONSUMER = "payment-authorized-processor-consumer-group"
    const val PAYMENT_ORDER_ENQUEUER = "payment-order-enqueuer-consumer-group"
    const val PAYMENT_ORDER_CAPTURE_EXECUTOR = "payment-order-capture-executor-consumer-group"
    const val PAYMENT_ORDER_PSP_RESULT_UPDATED = "payment-order-psp-result-updated-consumer-group"
    const val LEDGER_RECORDING_REQUEST_DISPATCHER = "ledger-recording-request-dispatcher-consumer-group"
    const val LEDGER_RECORDING_CONSUMER = "ledger-recording-consumer-group"
    const val ACCOUNT_BALANCE_CONSUMER = "account-balance-consumer-group"
}