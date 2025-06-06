package com.dogancaglar.paymentservice.config.messaging

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.paymentservice.application.event.*
import com.fasterxml.jackson.core.type.TypeReference

object EventMetadatas {


    object PaymentOrderCreatedMetadata : EventMetadata<PaymentOrderCreated> {
        override val topic = "payment_order_created_queue"
        override val eventType = "payment_order_created"
        override val clazz = PaymentOrderCreated::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCreated>>() {}
    }

    object PaymentOrderRetryRequestedMetadata : EventMetadata<PaymentOrderRetryRequested> {
        override val topic = "payment_order_retry_request_topic"
        override val eventType = "payment_order_retry"
        override val clazz = PaymentOrderRetryRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderRetryRequested>>() {}
    }

    object PaymentOrderStatusCheckScheduledMetadata : EventMetadata<PaymentOrderStatusCheckRequested> {
        override val topic = "payment_status_check_scheduler_topic"
        override val eventType = "payment_order_status_check_scheduled"
        override val clazz = PaymentOrderStatusCheckRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderStatusCheckRequested>>() {}
    }

    object PaymentOrderSuccededMetaData : EventMetadata<PaymentOrderSucceeded> {
        override val topic = "payment_order_succeded_topic"
        override val eventType = "payment_order_succeded"
        override val clazz = PaymentOrderSucceeded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderSucceeded>>() {}
    }

    val all: List<EventMetadata<*>> = listOf(
        PaymentOrderCreatedMetadata,
        PaymentOrderRetryRequestedMetadata,
        PaymentOrderStatusCheckScheduledMetadata, //PaymentOrderStatusCheckExecutorMetadata,
        PaymentOrderSuccededMetaData
    )
}