package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.common.event.EVENT_TYPE
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.event.Topics
import com.dogancaglar.paymentservice.domain.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.PaymentOrderStatusCheckRequested
import com.fasterxml.jackson.core.type.TypeReference

object EventMetadatas {


    object PaymentOrderCreatedMetadata : EventMetadata<PaymentOrderCreated> {
        override val topic = Topics.PAYMENT_ORDER_CREATED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CREATED
        override val clazz = PaymentOrderCreated::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCreated>>() {}
    }

    object PaymentOrderRetryRequestedMetadata : EventMetadata<PaymentOrderRetryRequested> {
        override val topic = Topics.PAYMENT_ORDER_RETRY
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_RETRY_REQUESTED
        override val clazz = PaymentOrderRetryRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderRetryRequested>>() {}
    }

    object PaymentOrderSucceededMetadata : EventMetadata<PaymentOrderSucceeded> {
        override val topic = Topics.PAYMENT_ORDER_SUCCEEDED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_SUCCEDED
        override val clazz = PaymentOrderSucceeded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderSucceeded>>() {}
    }

    object PaymentOrderStatusCheckScheduledMetadata : EventMetadata<PaymentOrderStatusCheckRequested> {
        override val topic = Topics.PAYMENT_STATUS_CHECK
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_STATUS_CHECK_REQUESTED
        override val clazz = PaymentOrderStatusCheckRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderStatusCheckRequested>>() {}
    }


    val all: List<EventMetadata<*>> = listOf(
        PaymentOrderCreatedMetadata,
        PaymentOrderRetryRequestedMetadata,
        PaymentOrderSucceededMetadata,
        PaymentOrderStatusCheckScheduledMetadata
    )

}