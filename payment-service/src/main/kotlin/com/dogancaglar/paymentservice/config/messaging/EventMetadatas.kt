package com.dogancaglar.paymentservice.config.messaging

import com.dogancaglar.common.event.EVENT_TYPE
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.event.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.application.event.PaymentOrderSucceeded
import com.fasterxml.jackson.core.type.TypeReference

object EventMetadatas {


    object PaymentOrderCreatedMetadata : EventMetadata<PaymentOrderCreated> {
        override val topic = TOPIC_NAMES.PAYMENT_ORDER_CREATED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CREATED
        override val clazz = PaymentOrderCreated::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCreated>>() {}
    }

    object PaymentOrderRetryRequestedMetadata : EventMetadata<PaymentOrderRetryRequested> {
        override val topic = TOPIC_NAMES.PAYMENT_ORDER_RETRY
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_RETRY_REQUESTED
        override val clazz = PaymentOrderRetryRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderRetryRequested>>() {}
    }

    object PaymentOrderStatusCheckScheduledMetadata : EventMetadata<PaymentOrderStatusCheckRequested> {
        override val topic = TOPIC_NAMES.PAYMENT_STATUS_CHECK_SCHEDULER
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_STATUS_CHECK_REQUESTED
        override val clazz = PaymentOrderStatusCheckRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderStatusCheckRequested>>() {}
    }

    object PaymentOrderSuccededMetaData : EventMetadata<PaymentOrderSucceeded> {
        override val topic = TOPIC_NAMES.PAYMENT_ORDER_SUCCEDED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_SUCCEDED
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