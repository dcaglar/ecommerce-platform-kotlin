package com.dogancaglar.common.kafka.metadata

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.metadata.EventMetadata
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.fasterxml.jackson.core.type.TypeReference
import com.dogancaglar.paymentservice.application.events.CaptureReceived
import com.dogancaglar.paymentservice.application.events.CaptureSuccessful
import com.dogancaglar.paymentservice.application.events.ExternalAsyncCaptureToPspPerformed
import com.dogancaglar.paymentservice.application.events.InternalTransferRequest

object PaymentEventMetadataCatalog {

    object PaymentAuthorizedMetadata : EventMetadata<PaymentAuthorized> {
        override val topic = Topics.PSP_RESULT_QUEUE
        override val eventType = EVENT_TYPE.PAYMENT_AUTHORIZED
        override val clazz = PaymentAuthorized::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentAuthorized>>() {}
        override val partitionKey = { evt: PaymentAuthorized ->
            evt.paymentIntentId
        }
    }


    object CaptureReceivedMetadata : EventMetadata<CaptureReceived> {
        override val topic = Topics.CAPTURE_EXECUTION_QUEUE
        override val eventType = EVENT_TYPE.CAPTURE_RECEIVED
        override val clazz = CaptureReceived::class.java
        override val typeRef = object : TypeReference<EventEnvelope<CaptureReceived>>() {}
        override val partitionKey = { evt: CaptureReceived ->
            evt.paymentIntentId
        }
    }

    object ExternalAsyncCaptureToPspPerformedMetadata : EventMetadata<ExternalAsyncCaptureToPspPerformed> {
        override val topic = Topics.CAPTURE_PSP_PERFORMED_QUEUE
        override val eventType = EVENT_TYPE.EXTERNAL_ASYNC_CAPTURE_PSP_PERFORMED
        override val clazz = ExternalAsyncCaptureToPspPerformed::class.java
        override val typeRef = object : TypeReference<EventEnvelope<ExternalAsyncCaptureToPspPerformed>>() {}
        override val partitionKey = { evt: ExternalAsyncCaptureToPspPerformed ->
            evt.paymentIntentId
        }
    }

    object CaptureSuccessfulMetadata : EventMetadata<CaptureSuccessful> {
        override val topic = Topics.PSP_RESULT_QUEUE // Maps to psp-result-queue
        override val eventType = EVENT_TYPE.CAPTURE_SUCCESSFUL
        override val clazz = CaptureSuccessful::class.java
        override val typeRef = object : TypeReference<EventEnvelope<CaptureSuccessful>>() {}
        override val partitionKey = { evt: CaptureSuccessful ->
            evt.merchantAccountId
        }
    }

    object InternalTransferRequestMetadata : EventMetadata<InternalTransferRequest> {
        override val topic = Topics.INTERNAL_TRANSFER_QUEUE
        override val eventType = EVENT_TYPE.INTERNAL_TRANSFER_REQUEST
        override val clazz = InternalTransferRequest::class.java
        override val typeRef = object : TypeReference<EventEnvelope<InternalTransferRequest>>() {}
        override val partitionKey = { evt: InternalTransferRequest ->
            evt.targetAccountId
        }
    }

    val all: List<EventMetadata<*>> = listOf(
        PaymentAuthorizedMetadata,
        CaptureReceivedMetadata,
        ExternalAsyncCaptureToPspPerformedMetadata,
        CaptureSuccessfulMetadata,
        InternalTransferRequestMetadata
    )

}