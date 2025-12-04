package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentUseCase
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.commands.AuthorizePaymentCommand
import com.dogancaglar.paymentservice.ports.outbound.*
import org.slf4j.LoggerFactory
import java.util.*

class AuthorizePaymentService(
    private val paymentRepository: PaymentRepository,
    private val psp: PspAuthGatewayPort,
    private val outboxEventPort: OutboxEventRepository,
    private val serializationPort: SerializationPort,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
) : AuthorizePaymentUseCase {

    override fun authorize(cmd: AuthorizePaymentCommand): Payment {
        val payment = paymentRepository.findById(cmd.paymentId)
            ?: error("Payment ${cmd.paymentId.value} not found")

        val updated = when (psp.authorize(payment)) {
            PaymentStatus.AUTHORIZED -> payment.authorize()
            PaymentStatus.DECLINED   -> payment.decline()
            else                     -> payment
        }

        paymentRepository.updatePayment(updated)

        if (updated.status == PaymentStatus.AUTHORIZED) {
            val outbox = toOutboxEvent(updated)
            outboxEventPort.save(outbox)
        }

        return updated
    }

    private fun toOutboxEvent(payment: Payment): OutboxEvent {
        val event = paymentOrderDomainEventMapper.toPaymentAuthorized(payment, payment.paymentLines)

        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = EventLogContext.getTraceId(),
            data = event,
            aggregateId = payment.paymentId.value.toString()
        )

        return OutboxEvent.createNew(
            oeid = payment.paymentId.value,
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = serializationPort.toJson(envelope)
        )
    }
}