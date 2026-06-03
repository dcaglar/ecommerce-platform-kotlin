package com.dogancaglar.common.kafka.metadata

object EVENT_TYPE {
    const val PAYMENT_AUTHORIZED = "payment_authorized"
    const val LEDGER_ENTRIES_RECORDED = "ledger_entries_recorded"
    const val CAPTURE_RECEIVED = "capture_received"
    const val EXTERNAL_ASYNC_CAPTURE_PSP_PERFORMED = "external_async_capture_psp_performed"
    const val CAPTURE_SUCCESSFUL = "capture_successful"
    const val INTERNAL_TRANSFER_REQUEST = "internal_transfer_request"
}


object Topics {
    const val PSP_RESULT_QUEUE = "psp-result-queue"
    const val LEDGER_ENTRIES_RECORDED = "ledger_entries_recorded_topic"
    const val CAPTURE_EXECUTION_QUEUE = "capture-execution-queue"
    const val CAPTURE_PSP_PERFORMED_QUEUE = "capture_psp_performed_queue"
    const val INTERNAL_TRANSFER_QUEUE = "internal-transfer-queue"
    
    fun dlqOf(topic: String) = "$topic.DLQ"

    val ALL = listOf(
        PSP_RESULT_QUEUE,
        LEDGER_ENTRIES_RECORDED,
        CAPTURE_EXECUTION_QUEUE,
        CAPTURE_PSP_PERFORMED_QUEUE,
        INTERNAL_TRANSFER_QUEUE
    )
}

object CONSUMER_GROUPS {
    const val MARKETPLACE_SPLIT_INSTRUCTION_CONSUMER= "marketplace-split-instruction-consumer-group"
    const val CAPTURE_COMMAND_EXECUTOR = "capture-command-executor-consumer-group"
    const val CAPTURE_PSP_PERFORMED = "capture-psp-performed-consumer-group"
    const val INTERNAL_TRANSFER_CONSUMER = "internal-transfer-consumer-group"
    const val PSP_RESULT_CONSUMER = "psp-result-consumer-group"
    const val ACCOUNT_BALANCE_CONSUMER = "account-balance-consumer-group"
}