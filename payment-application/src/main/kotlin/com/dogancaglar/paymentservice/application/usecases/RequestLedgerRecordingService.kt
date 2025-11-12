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
        if (!event.status.equals("SUCCESSFUL_FINAL", ignoreCase = true) &&
            !event.status.equals("FAILED_FINAL", ignoreCase = true)) {
            logger.info("⏩ Skipping ledger recording for non-final status={}", event.status)
            return
        }
        val requested = LedgerRecordingCommand(
            paymentOrderId = event.paymentOrderId,
            paymentId = event.paymentId,
            sellerId = event.sellerId,
            amountValue = event.amountValue,
            currency = event.currency,
            status = event.status,
            createdAt = LocalDateTime.now(clock)
        )

        eventPublisherPort.publishSync(
            eventMetaData = EventMetadatas.LedgerRecordingCommandMetadata,
            aggregateId = requested.sellerId,
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