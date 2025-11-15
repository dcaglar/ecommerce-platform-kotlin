package com.dogancaglar.paymentservice.application.metadata

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.LedgerEntriesRecorded
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.events.PaymentOrderFailed
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.application.events.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.application.commands.LedgerRecordingCommand
import com.fasterxml.jackson.core.type.TypeReference

object EventMetadatas {


    object PaymentOrderCreatedMetadata : EventMetadata<PaymentOrderCreated> {
        override val topic = Topics.PAYMENT_ORDER_CREATED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CREATED
        override val clazz = PaymentOrderCreated::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCreated>>() {}
        override val partitionKeyExtractor = { evt: PaymentOrderCreated ->
            evt.paymentOrderId   // <-- INTERNAL ID string
        }
    }




    object PaymentAuthorizedMetadata : EventMetadata<PaymentAuthorized> {
        override val topic = Topics.PAYMENT_AUTHORIZED
        override val eventType = EVENT_TYPE.PAYMENT_AUTHORIZED
        override val clazz = PaymentAuthorized::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentAuthorized>>() {}
        override val partitionKeyExtractor = { evt: PaymentAuthorized ->
            evt.paymentId   // <-- INTERNAL ID string
        }
    }

    object PaymentOrderCaptureCommandMetadata : EventMetadata<PaymentOrderCaptureCommand> {
        override val topic = Topics.PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CAPTURE_REQUESTED
        override val clazz = PaymentOrderCaptureCommand::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCaptureCommand>>() {}
        override val partitionKeyExtractor = { evt: PaymentOrderCaptureCommand ->
            evt.paymentOrderId
        }
    }

    object PaymentOrderPspResultUpdatedMetadata : EventMetadata<PaymentOrderPspResultUpdated> {
        override val topic = Topics.PAYMENT_ORDER_PSP_RESULT_UPDATED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_PSP_RESULT_UPDATED
        override val clazz = PaymentOrderPspResultUpdated::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderPspResultUpdated>>() {}
        override val partitionKeyExtractor = { evt: PaymentOrderPspResultUpdated ->
            evt.paymentOrderId
        }
    }

    object PaymentOrderSucceededMetadata : EventMetadata<PaymentOrderSucceeded> {
        override val topic = Topics.PAYMENT_ORDER_FINALIZED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_SUCCEDED
        override val clazz = PaymentOrderSucceeded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderSucceeded>>() {}
        override val partitionKeyExtractor = { evt: PaymentOrderSucceeded ->
            evt.paymentOrderId
        }
    }

    object PaymentOrderFailedMetadata : EventMetadata<PaymentOrderFailed> {
        override val topic = Topics.PAYMENT_ORDER_FINALIZED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_FAILED
        override val clazz = PaymentOrderFailed::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderFailed>>() {}
        override val partitionKeyExtractor = { evt: PaymentOrderFailed ->
            evt.paymentOrderId
        }
    }

    object LedgerRecordingCommandMetadata : EventMetadata<LedgerRecordingCommand> {
        override val topic = Topics.LEDGER_RECORD_REQUEST_QUEUE
        override val eventType = EVENT_TYPE.LEDGER_RECORDING_REQUESTED
        override val clazz = LedgerRecordingCommand::class.java
        override val typeRef = object : TypeReference<EventEnvelope<LedgerRecordingCommand>>() {}
        override val partitionKeyExtractor = { evt: LedgerRecordingCommand ->
            evt.sellerId
        }
    }

    object LedgerEntriesRecordedMetadata : EventMetadata<LedgerEntriesRecorded> {
        override val topic = Topics.LEDGER_ENTRIES_RECORDED
        override val eventType = EVENT_TYPE.LEDGER_ENTRIES_RECORDED
        override val clazz = LedgerEntriesRecorded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<LedgerEntriesRecorded>>() {}
        override val partitionKeyExtractor = { evt: LedgerEntriesRecorded ->
            evt.sellerId
        }
    }




    val all: List<EventMetadata<*>> = listOf(
        PaymentAuthorizedMetadata,
        PaymentOrderCreatedMetadata,
        PaymentOrderCaptureCommandMetadata,
        PaymentOrderSucceededMetadata,
        PaymentOrderFailedMetadata,
        PaymentOrderPspResultUpdatedMetadata,
        LedgerRecordingCommandMetadata,
        LedgerEntriesRecordedMetadata
    )

}