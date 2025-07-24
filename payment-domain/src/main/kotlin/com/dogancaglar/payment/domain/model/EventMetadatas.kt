package com.dogancaglar.payment.domain.model


import com.dogancaglar.common.event.CONSUMER_GROUPS.PAYMENT_ORDER_CREATED
import com.dogancaglar.common.event.CONSUMER_GROUPS.PAYMENT_ORDER_RETRY
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.payment.domain.PaymentOrderCreated
import com.dogancaglar.payment.domain.PaymentOrderRetryRequested
import com.dogancaglar.payment.domain.PaymentOrderSucceeded
import com.fasterxml.jackson.core.type.TypeReference

object EventMetadatas {


    object PaymentOrderCreatedMetadata : EventMetadata<PaymentOrderCreated> {
        override val topic = PAYMENT_ORDER_CREATED
        override val eventType = EventMetadatas.PaymentOrderCreatedMetadata.topic
        override val clazz = PaymentOrderCreated::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCreated>>() {}
    }

    object PaymentOrderRetryRequestedMetadata : EventMetadata<PaymentOrderRetryRequested> {
        override val topic = PAYMENT_ORDER_RETRY
        override val eventType = EventMetadatas.PaymentOrderRetryRequestedMetadata.topic
        override val clazz = PaymentOrderRetryRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderRetryRequested>>() {}
    }

    object PaymentOrderSucceededMetadata : EventMetadata<PaymentOrderSucceeded> {
        override val topic = PAYMENT_ORDER_CREATED
        override val eventType = EventMetadatas.PaymentOrderSucceededMetadata.topic
        override val clazz = PaymentOrderSucceeded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderSucceeded>>() {}
    }


    val all: List<EventMetadata<*>> = listOf(
        PaymentOrderCreatedMetadata,
        PaymentOrderRetryRequestedMetadata,
        PaymentOrderSucceededMetadata
    )

}