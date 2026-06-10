package com.dogancaglar.common.kafka.metadata



object Topics {
    // SHARED STREAMS (Multiple Event Types)
    // ---------------------------------------------------------
    // Holds both PAYMENT_AUTHORIZED and CAPTURE_CONFIRMED
    const val PSP_RESULTS = "payment.psp.results"

    // ---------------------------------------------------------
    // ISOLATED STREAMS (Specific Commands & ACKs)
    // ---------------------------------------------------------
    const val CAPTURE_COMMANDS = "gateway.capture.commands"
    const val CAPTURE_SUBMITTED_ACKS = "gateway.capture.submitted"

    const val INTERNAL_TRANSFERS = "ledger.internal.transfers"
    const val JOURNAL_ENTRIES_RECORDED = "journal.entries.recorded"
    
    fun dlqOf(topic: String) = "$topic.DLQ"

    val ALL = listOf(
        PSP_RESULTS,
        JOURNAL_ENTRIES_RECORDED,
        CAPTURE_COMMANDS,
        CAPTURE_SUBMITTED_ACKS,
        INTERNAL_TRANSFERS
    )
}

object CONSUMER_GROUPS {
    const val PSP_RESULT_CONSUMER = "payment-core.psp-result-consumer"
    const val CAPTURE_COMMAND_EXECUTOR = "gateway-workers.capture-command-executor"
    const val CAPTURE_SUBMITTED_CONSUMER = "payment-core.capture-submitted"
    const val GROSS_CAPTURE_ALLOCATION_CONSUMER= "payment.gross-capture-allocation-consumer-group"
    const val INTERNAL_TRANSFER_CONSUMER = "ledger-engine.internal-transfer-consumer"
    const val ACCOUNT_BALANCE_CONSUMER = "ledger-engine.account-balance-consumer"

    /*
    const val PSP_RESULT_CONSUMER = "payment-core.psp-result-consumer"
    const val CAPT================================        URE_PSP_PERFORMED_CONSUMER = "payment-core.capture-psp-performed-consumer"

    // Gateway Worker consumers
    const val CAPTURE_COMMAND_EXECUTOR = "gateway-workers.capture-command-executor"

    // Ledger consumers
    const val INTERNAL_TRANSFER_CONSUMER = "ledger-engine.internal-transfer-consumer"
    const val ACCOUNT_BALANCE_CONSUMER = "ledger-engine.account-balance-consumer"
     */
}