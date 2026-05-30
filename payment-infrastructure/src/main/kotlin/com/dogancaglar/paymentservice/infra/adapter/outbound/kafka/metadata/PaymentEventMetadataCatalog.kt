package com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.metadata.EventMetadata
import com.dogancaglar.paymentservice.application.command.LedgerRecordingCommand
import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefundReceived
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptured
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefunded
import com.fasterxml.jackson.core.type.TypeReference

object PaymentEventMetadataCatalog {


    object PaymentOrderCreatedMetadata : EventMetadata<PaymentOrderCaptureReceived> {
        override val topic = Topics.CAPTURE_QUEUE
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CREATED
        override val clazz = PaymentOrderCaptureReceived::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCaptureReceived>>() {}
        override val partitionKey = { evt: PaymentOrderCaptureReceived ->
            evt.paymentOrderId   // <-- INTERNAL ID string
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

    object PaymentOrderCapturedMetadata : EventMetadata<PaymentOrderCaptured> {
        override val topic = Topics.PAYMENT_ORDER_CAPTURED_TOPIC
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CAPTURED
        override val clazz = PaymentOrderCaptured::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCaptured>>() {}
        override val partitionKey = { evt: PaymentOrderCaptured ->
            evt.paymentOrderId
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
            evt.paymentId   // <-- INTERNAL ID string
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

    object PaymentOrderPspResultUpdatedMetadata : EventMetadata<PaymentOrderPspResultUpdated> {
        override val topic = Topics.PSP_RESULT
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_PSP_RESULT_UPDATED
        override val clazz = PaymentOrderPspResultUpdated::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderPspResultUpdated>>() {}
        override val partitionKey = { evt: PaymentOrderPspResultUpdated ->
            evt.paymentOrderId
        }
    }

    object PaymentOrderFinalizedMetadata : EventMetadata<PaymentOrderFinalized> {
        override val topic = Topics.PAYMENT_ORDER_FINALIZED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_FINALIZED
        override val clazz = PaymentOrderFinalized::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderFinalized>>() {}
        override val partitionKey = { evt: PaymentOrderFinalized ->
            evt.paymentOrderId
        }
    }

    object LedgerRecordingCommandMetadata : EventMetadata<LedgerRecordingCommand> {
        override val topic = Topics.LEDGER_RECORD_REQUEST_QUEUE
        override val eventType = EVENT_TYPE.LEDGER_RECORDING_REQUESTED
        override val clazz = LedgerRecordingCommand::class.java
        override val typeRef = object : TypeReference<EventEnvelope<LedgerRecordingCommand>>() {}
        override val partitionKey = { evt: LedgerRecordingCommand ->
            evt.sellerId
        }
    }

    object LedgerEntriesRecordedMetadata : EventMetadata<LedgerEntriesRecorded> {
        override val topic = Topics.LEDGER_ENTRIES_RECORDED
        override val eventType = EVENT_TYPE.LEDGER_ENTRIES_RECORDED
        override val clazz = LedgerEntriesRecorded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<LedgerEntriesRecorded>>() {}
        override val partitionKey = { evt: LedgerEntriesRecorded ->
            evt.sellerId
        }
    }




    val all: List<EventMetadata<*>> = listOf(
        PaymentAuthorizedMetadata,
        PaymentOrderCreatedMetadata,
        PaymentOrderRefundReceivedMetadata,
        PaymentOrderCaptureCommandMetadata,
        PaymentOrderFinalizedMetadata,
        PaymentOrderPspResultUpdatedMetadata,
        PaymentOrderCapturedMetadata,
        PaymentOrderRefundedMetadata,
        LedgerRecordingCommandMetadata,
        LedgerEntriesRecordedMetadata
    )

}