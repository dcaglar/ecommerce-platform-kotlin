package com.dogancaglar.common.kafka.metadata

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.metadata.EventMetadata
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.fasterxml.jackson.core.type.TypeReference
import com.dogancaglar.paymentservice.application.events.CaptureRequested
import com.dogancaglar.paymentservice.application.events.CaptureConfirmed
import com.dogancaglar.paymentservice.application.events.EventType
import com.dogancaglar.paymentservice.application.events.CaptureSubmitted
import com.dogancaglar.paymentservice.application.events.InternalTransferRequested

object PaymentEventMetadataCatalog {

    // 1. Routes to shared PSP_RESULTS topic
    object PaymentAuthorizedMetadata : EventMetadata<PaymentAuthorized> {
        override val topic = Topics.PSP_RESULTS
        override val eventType = EventType.PAYMENT_AUTHORIZED
        override val clazz = PaymentAuthorized::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentAuthorized>>() {}
        override val partitionKey = { evt: PaymentAuthorized -> evt.paymentIntentId }
    }

    // 2. Routes to CAPTURE_COMMANDS for the PaymentCaptureExecutor
    object CaptureRequestedMetadata : EventMetadata<CaptureRequested> {
        override val topic = Topics.CAPTURE_COMMANDS
        override val eventType = EventType.CAPTURE_REQUESTED
        override val clazz = CaptureRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<CaptureRequested>>() {}
        override val partitionKey = { evt: CaptureRequested -> evt.paymentIntentId }
    }

    // 3. Routes to CAPTURE_SUBMITTED_ACKS (The HTTP 202)
    object CaptureSubmittedMetadata : EventMetadata<CaptureSubmitted> {
        override val topic = Topics.CAPTURE_SUBMITTED_ACKS
        override val eventType = EventType.CAPTURE_SUBMITTED
        override val clazz = CaptureSubmitted::class.java
        override val typeRef = object : TypeReference<EventEnvelope<CaptureSubmitted>>() {}
        override val partitionKey = { evt: CaptureSubmitted -> evt.paymentIntentId }
    }

    // 4. Routes to shared PSP_RESULTS topic (The Adyen Webhook)
    object CaptureConfirmedMetadata : EventMetadata<CaptureConfirmed> {
        override val topic = Topics.PSP_RESULTS
        override val eventType = EventType.CAPTURE_CONFIRMED
        override val clazz = CaptureConfirmed::class.java
        override val typeRef = object : TypeReference<EventEnvelope<CaptureConfirmed>>() {}
        // Use publicPaymentIntentId here to ensure it lands in the exact same partition as PaymentAuthorized
        override val partitionKey = { evt: CaptureConfirmed -> evt.publicPaymentIntentId }
    }

    // 5. Routes to INTERNAL_TRANSFERS
    object InternalTransferRequestedMetadata : EventMetadata<InternalTransferRequested> {
        override val topic = Topics.INTERNAL_TRANSFERS
        override val eventType = EventType.INTERNAL_TRANSFER_REQUESTED
        override val clazz = InternalTransferRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<InternalTransferRequested>>() {}
        override val partitionKey = { evt: InternalTransferRequested -> evt.targetEntityId }
    }

    val all: List<EventMetadata<*>> = listOf(
        PaymentAuthorizedMetadata,
        CaptureRequestedMetadata,
        CaptureSubmittedMetadata,
        CaptureConfirmedMetadata,
        InternalTransferRequestedMetadata
    )

}