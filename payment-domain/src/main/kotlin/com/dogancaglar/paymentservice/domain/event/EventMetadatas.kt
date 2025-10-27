package com.dogancaglar.paymentservice.domain.event

import com.dogancaglar.common.event.EVENT_TYPE
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.common.event.Topics
import com.dogancaglar.paymentservice.domain.PaymentOrderStatusCheckRequested
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.fasterxml.jackson.core.type.TypeReference

object EventMetadatas {


    object PaymentOrderCreatedMetadata : EventMetadata<PaymentOrderCreated> {
        override val topic = Topics.PAYMENT_ORDER_CREATED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_CREATED
        override val clazz = PaymentOrderCreated::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderCreated>>() {}
    }

    object PaymentOrderPspCallRequestedMetadata : EventMetadata<PaymentOrderPspCallRequested> {
        override val topic = Topics.PAYMENT_ORDER_PSP_CALL_REQUESTED
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_PSP_CALL_REQUESTED
        override val clazz = PaymentOrderPspCallRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderPspCallRequested>>() {}
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



    object PaymentOrderStatusCheckScheduledMetadata : EventMetadata<PaymentOrderStatusCheckRequested> {
        override val topic = Topics.PAYMENT_STATUS_CHECK
        override val eventType = EVENT_TYPE.PAYMENT_ORDER_STATUS_CHECK_REQUESTED
        override val clazz = PaymentOrderStatusCheckRequested::class.java
        override val typeRef = object : TypeReference<EventEnvelope<PaymentOrderStatusCheckRequested>>() {}
    }


    val all: List<EventMetadata<*>> = listOf(
        PaymentOrderCreatedMetadata,
        PaymentOrderPspCallRequestedMetadata,
        PaymentOrderSucceededMetadata,
        PaymentOrderFailedMetadata,
        PaymentOrderStatusCheckScheduledMetadata,
        PaymentOrderPspResultUpdatedMetadata,
        LedgerRecordingCommandMetadata,
        LedgerEntriesRecordedMetadata
    )

}