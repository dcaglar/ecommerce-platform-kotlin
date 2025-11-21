package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.constants.PaymentLogFields
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.commands.CapturePaymentCommand
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentUseCase
import com.dogancaglar.paymentservice.ports.inbound.CapturePaymentUseCase
import com.dogancaglar.paymentservice.ports.outbound.*
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime
import java.util.*


class CapturePaymentService(
    private val paymentRepository: PaymentRepository,
    private val psp: PspAuthGatewayPort,
    private val outboxEventPort: OutboxEventRepository,
    private val idGeneratorPort: IdGeneratorPort,
    private val serializationPort: SerializationPort,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper) : CapturePaymentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)


    override fun capture(cmd: CapturePaymentCommand): PaymentOrder? {
        // 1️⃣check if authorization exist as pre-validation, check if thehere is paymentorder exist with status INTIATED_PENDING or not if not return bad-request.
            //todo what validation are we gonna do here, are we gona check only if auth exist?

            // 2️⃣ Persist a OutboxEvent<PAymentORderCaptureCommand>, but how are we gonna check outbox event dowe ahve anought information
                //return pending result
        return null
    }

    private fun toOutboxEvent(updated: Payment,paymentLines: List<PaymentLine>): OutboxEvent {
        val paymentAuthorizedEvent = paymentOrderDomainEventMapper.toPaymentAuthorized(updated,paymentLines)
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = EventLogContext.getTraceId() ?: UUID.randomUUID().toString(),
            data = paymentAuthorizedEvent,
            aggregateId = updated.paymentId.value.toString()
        )

        val extraLogFields = mapOf(
            PaymentLogFields.PUBLIC_PAYMENT_ID to updated.paymentId.toPublicPaymentId()
        )

        EventLogContext.with(envelope, additionalContext = extraLogFields) {
            logger.debug(
                "Creating OutboxEvent for eventType={}, aggregateId={}, eventId={}",
                envelope.eventType,
                envelope.aggregateId,
                envelope.eventId
            )
        }

        return OutboxEvent.createNew(
            oeid = updated.paymentId.value,
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = serializationPort.toJson(envelope),
        )
    }
}