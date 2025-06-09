package com.dogancaglar.paymentservice.config.metrics

object MetricTags {
    // Common keys
    const val JOB = "job"
    const val EVENT_TYPE = "eventType"
    const val TOPIC = "topic"
    const val STATUS = "status"
    const val GROUP = "group"
    const val REASON = "reason"
    const val PARTITION = "partition"
    // ...add more as you standardize

    // Optional: Common values (for job names etc.)
    object Jobs {
        const val OUTBOX_DISPATCHER = "outboxPaymentOrderDispatcher"
        const val RETRY_SCHEDULER = "retryScheduler"
        // ...
    }

    object EventTypes {
        const val PAYMENT_ORDER_CREATED = "payment_order_created"
        const val PAYMENT_CREATED = "payment_created"
        // ...
    }
}