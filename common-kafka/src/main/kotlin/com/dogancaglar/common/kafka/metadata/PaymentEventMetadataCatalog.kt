package com.dogancaglar.common.kafka.metadata

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.metadata.EventMetadata
import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefundReceived
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentCaptured
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefunded
import com.fasterxml.jackson.core.type.TypeReference
import com.dogancaglar.paymentservice.application.events.CaptureReceived
import com.dogancaglar.paymentservice.application.events.CaptureSuccessful
import com.dogancaglar.paymentservice.application.events.ExternalAsyncCaptureToPspPerformed
import com.dogancaglar.paymentservice.application.events.InternalTransferRequest

object PaymentEventMetadataCatalog {

    object PaymentOrderCreatedMetadata : EventMetadata<PaymentOrderCaptureReceived> {
        override val topic = Topics.CAPTURE_QUEUE
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CREATED
        override val clazz = PaymentOrderCaptureReceived::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCaptureReceived>>() {}
        override val partitionKey = { evt: PaymentOrderCaptureReceived ->
            evt.paymentOrderId
        }
    }

    object PaymentOrderRefundReceivedMetadata : EventMetadata<PaymentOrderRefundReceived> {
        override val topic = Topics.REFUND_QUEUE
        override val eventType = "payment_order_refund_received"
        override val clazz = PaymentOrderRefundReceived::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderRefundReceived>>() {}
        override val partitionKey = { evt: PaymentOrderRefundReceived ->
            evt.paymentOrderId
        }
    }

    object PaymentOrderCapturedMetadata : EventMetadata<PaymentCaptured> {
        override val topic = Topics.PAYMENT_ORDER_CAPTURED_TOPIC
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CAPTURED
        override val clazz = PaymentCaptured::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentCaptured>>() {}
        override val partitionKey = { evt: PaymentCaptured ->
            evt.paymentIntentId
        }
    }

    object PaymentOrderRefundedMetadata : EventMetadata<PaymentOrderRefunded> {
        override val topic = Topics.PAYMENT_ORDER_REFUNDED_TOPIC
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_REFUNDED
        override val clazz = PaymentOrderRefunded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderRefunded>>() {}
        override val partitionKey = { evt: PaymentOrderRefunded ->
            evt.paymentOrderId
        }
    }

    object PaymentAuthorizedMetadata : EventMetadata<PaymentAuthorized> {
        override val topic = Topics.PAYMENT_AUTHORIZED
        override val eventType = EVENT_TYPE.PAYMENT_AUTHORIZED
        override val clazz = PaymentAuthorized::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentAuthorized>>() {}
        override val partitionKey = { evt: PaymentAuthorized ->
            evt.paymentIntentId
        }
    }

    object PaymentOrderCaptureCommandMetadata : EventMetadata<PaymentOrderCaptureCommand> {
        override val topic = Topics.PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CAPTURE_REQUESTED
        override val clazz = PaymentOrderCaptureCommand::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCaptureCommand>>() {}
        override val partitionKey = { evt: PaymentOrderCaptureCommand ->
            evt.paymentOrderId
        }
    }

    object LedgerEntriesRecordedMetadata : EventMetadata<LedgerEntriesRecorded> {
        override val topic = Topics.LEDGER_ENTRIES_RECORDED
        override val eventType = EVENT_TYPE.LEDGER_ENTRIES_RECORDED
        override val clazz = LedgerEntriesRecorded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<LedgerEntriesRecorded>>() {}
        override val partitionKey = { evt: LedgerEntriesRecorded ->
            evt.sellerId ?: "GLOBAL"
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
        override val topic = Topics.PAYMENT_AUTHORIZED // Maps to psp-result-queue
        override val eventType = EVENT_TYPE.CAPTURE_SUCCESSFUL
        override val clazz = CaptureSuccessful::class.java
        override val typeRef = object : TypeReference<EventEnvelope<CaptureSuccessful>>() {}
        override val partitionKey = { evt: CaptureSuccessful ->
            evt.paymentIntentId
        }
    }

    object InternalTransferRequestMetadata : EventMetadata<InternalTransferRequest> {
        override val topic = Topics.INTERNAL_TRANSFER_QUEUE
        override val eventType = EVENT_TYPE.INTERNAL_TRANSFER_REQUEST
        override val clazz = InternalTransferRequest::class.java
        override val typeRef = object : TypeReference<EventEnvelope<InternalTransferRequest>>() {}
        override val partitionKey = { evt: InternalTransferRequest ->
            evt.paymentIntentId
        }
    }

    val all: List<EventMetadata<*>> = listOf(
        PaymentAuthorizedMetadata,
        PaymentOrderCreatedMetadata,
        PaymentOrderRefundReceivedMetadata,
        PaymentOrderCaptureCommandMetadata,
        PaymentOrderCapturedMetadata,
        PaymentOrderRefundedMetadata,
        LedgerEntriesRecordedMetadata,
        CaptureReceivedMetadata,
        ExternalAsyncCaptureToPspPerformedMetadata,
        CaptureSuccessfulMetadata,
        InternalTransferRequestMetadata
    )

}