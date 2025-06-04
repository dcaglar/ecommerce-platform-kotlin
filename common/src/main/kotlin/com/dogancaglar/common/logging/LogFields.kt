package com.dogancaglar.common.logging

object LogFields {
    const val TRACE_ID = "traceId"
    const val EVENT_TYPE = "eventType"
    const val EVENT_ID = "eventId"
    const val AGGREGATE_ID = "aggregateId"
    const val TOPIC_NAME = "topicName"
    const val CONSUMER_GROUP = "consumerGroup"
    const val PAYMENT_ORDER_ID = "paymentOrderId" //
    const val PAYMENT_ID = "paymentId" // now used for any public ID// now used for any public ID
    const val PUBLIC_PAYMENT_ORDER_ID = "publicPaymentOrderId"
    const val PUBLIC_PAYMENT_ID = "publicPaymentId"
    const val PARENT_EVENT_ID = "parentEventId"
    const val RETRY_COUNT = "retryCount"
    const val RETRY_REASON = "retryReason"
    const val RETRY_ERROR_MESSAGE = "retryErrorMessage"
    const val RETRY_BACKOFF_MILLIS = "retryBackoffMillis"

}