package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.domain.event.*
import com.dogancaglar.paymentservice.ports.inbound.RequestLedgerRecordingUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime

open class RequestLedgerRecordingService(
    private val eventPublisherPort: EventPublisherPort,
    private val clock: Clock
) : RequestLedgerRecordingUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun requestLedgerRecording(event: PaymentOrderEvent) {
        val requested = LedgerRecordingCommand(
            paymentOrderId = event.paymentOrderId,
            publicPaymentOrderId = event.publicPaymentOrderId,
            paymentId = event.paymentId,
            publicPaymentId = event.publicPaymentId,
            sellerId = event.sellerId,
            amountValue = event.amountValue,
            currency = event.currency,
            status = event.status,
            createdAt = LocalDateTime.now(clock)
        )

        eventPublisherPort.publishSync(
            eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
            aggregateId = requested.publicPaymentOrderId,
            data = requested,
            parentEventId = LogContext.getEventId(),
            traceId = LogContext.getTraceId()
        )

        logger.info(
            "📘 Published LedgerRecordingCommand for paymentOrderId={} status={}",
            event.publicPaymentOrderId, event.status
        )
    }
}