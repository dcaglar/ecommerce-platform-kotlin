package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.common.event.EVENT_TYPE
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.event.Topics
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.domain.commands.PaymentOrderCaptureCommand
import com.fasterxml.jackson.core.type.TypeReference

object EventMetadatas {


    object PaymentOrderCreatedMetadata : EventMetadata<PaymentOrderCreated> {
        override val topic = Topics.PAYMENT_ORDER_CREATED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CREATED
        override val clazz = PaymentOrderCreated::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCreated>>() {}
    }

    object PaymentOrderCaptureRequestedMetadata : EventMetadata<PaymentOrderCaptureCommand> {
        override val topic = Topics.PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CAPTURE_REQUESTED
        override val clazz = PaymentOrderCaptureCommand::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCaptureCommand>>() {}
    }

    object PaymentOrderPspResultUpdatedMetadata : EventMetadata<PaymentOrderPspResultUpdated> {
        override val topic = Topics.PAYMENT_ORDER_PSP_RESULT_UPDATED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_PSP_RESULT_UPDATED
        override val clazz = PaymentOrderPspResultUpdated::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderPspResultUpdated>>() {}
    }

    object PaymentOrderSucceededMetadata : EventMetadata<PaymentOrderSucceeded> {
        override val topic = Topics.PAYMENT_ORDER_FINALIZED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_SUCCEDED
        override val clazz = PaymentOrderSucceeded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderSucceeded>>() {}
    }

    object PaymentOrderFailedMetadata : EventMetadata<PaymentOrderFailed> {
        override val topic = Topics.PAYMENT_ORDER_FINALIZED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_FAILED
        override val clazz = PaymentOrderFailed::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderFailed>>() {}
    }

    object LedgerRecordingCommandMetadata : EventMetadata<LedgerRecordingCommand> {
        override val topic = Topics.LEDGER_RECORD_REQUEST_QUEUE
        override val eventType = EVENT_TYPE.LEDGER_RECORDING_REQUESTED
        override val clazz = LedgerRecordingCommand::class.java
        override val typeRef = object : TypeReference<EventEnvelope<LedgerRecordingCommand>>() {}
    }

    object LedgerEntriesRecordedMetadata : EventMetadata<LedgerEntriesRecorded> {
        override val topic = Topics.LEDGER_ENTRIES_RECORDED
        override val eventType = EVENT_TYPE.LEDGER_ENTRIES_RECORDED
        override val clazz = LedgerEntriesRecorded::class.java
        override val typeRef = object : TypeReference<EventEnvelope<LedgerEntriesRecorded>>() {}
    }




    val all: List<EventMetadata<*>> = listOf(
        PaymentOrderCreatedMetadata,
        PaymentOrderCaptureRequestedMetadata,
        PaymentOrderSucceededMetadata,
        PaymentOrderFailedMetadata,
        PaymentOrderPspResultUpdatedMetadata,
        LedgerRecordingCommandMetadata,
        LedgerEntriesRecordedMetadata
    )

}