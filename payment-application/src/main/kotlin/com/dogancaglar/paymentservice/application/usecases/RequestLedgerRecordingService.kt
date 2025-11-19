package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.commands.LedgerRecordingCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
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

    override fun requestLedgerRecording(event: PaymentOrderFinalized) {
        val requested = LedgerRecordingCommand.from(
            final = event,
            now = LocalDateTime.now(clock)
        )

        eventPublisherPort.publishSync(
            aggregateId = requested.sellerId,
            data = requested,
            parentEventId = EventLogContext.getEventId(),
            traceId = EventLogContext.getTraceId()
        )

        logger.info(
            "📘 Published LedgerRecordingCommand for paymentOrderId={} finalStatus={}",
            event.publicPaymentOrderId, requested.finalStatus
        )
    }
}