package com.dogancaglar.common.logging

object LogFields {
    const val TRACE_ID = "traceId"
    const val EVENT_ID = "eventId"
    const val AGGREGATE_ID = "aggregateId"
    const val EVENT_TYPE = "eventType"
    const val TOPIC_NAME = "topicName"
    const val CONSUMER_GROUP = "consumerGroup"
    const val PUBLIC_ID = "publicId" // now used for any public ID
    const val PUBLIC_PAYMENT_ORDER_ID = "paymentOrderId"
    const val PUBLIC_PAYMENT_ID = "paymentOrderId"
    const val PARENT_EVENT = "retryCount"
    const val RETRY_COUNT = "retryCount"
    const val RETRY_REASON = "retryReason"
}