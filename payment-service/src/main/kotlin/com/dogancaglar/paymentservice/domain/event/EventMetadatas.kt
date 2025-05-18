package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.fasterxml.jackson.core.type.TypeReference

object EventMetadatas {




    object PaymentOrderCreatedMetadata : EventMetadata<PaymentOrderCreated> {
        override val topic = "payment_order_created"
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

    object PaymentOrderStatusCheckRequestedMetadata : EventMetadata<PaymentOrderStatusCheckRequested> {
        override val topic = "payment_status_check"
        override val eventType = "payment_order_status_check_requested"
        override val clazz = PaymentOrderStatusCheckRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderStatusCheckRequested>>() {}
    }

    object ScheduledStatusCheckMetadata : EventMetadata<PaymentOrderStatusCheckRequested> {
        override val topic = "scheduled_status_check"
        override val eventType = "scheduled_payment_order_status_check_requested"
        override val clazz = PaymentOrderStatusCheckRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderStatusCheckRequested>>() {}
    }

    object PaymentOrderSuccededMetaData : EventMetadata<PaymentOrderSucceeded> {
        override val topic = "payment_order_created"
        override val eventType = "payment_order_created"
        override val clazz = PaymentOrderSucceeded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderSucceeded>>() {}
    }

    val all: List<EventMetadata<*>> = listOf(
        PaymentOrderCreatedMetadata,
        PaymentOrderRetryRequestedMetadata,
        PaymentOrderStatusCheckRequestedMetadata,
        ScheduledStatusCheckMetadata,
    )
}