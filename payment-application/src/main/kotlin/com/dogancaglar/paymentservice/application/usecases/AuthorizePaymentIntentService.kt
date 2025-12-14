package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentIntentUseCase
import com.dogancaglar.paymentservice.domain.commands.AuthorizePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.ports.outbound.*

class AuthorizePaymentIntentService(
    private val paymentIntentRepository: PaymentIntentRepository,
    private val psp: PspAuthGatewayPort,
    private val outboxEventPort: OutboxEventRepository,
    private val serializationPort: SerializationPort,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
) : AuthorizePaymentIntentUseCase {

    override fun authorize(cmd: AuthorizePaymentIntentCommand): PaymentIntent {
        val paymentIntent = paymentIntentRepository.findById(cmd.paymentIntentId)
            ?: error("Payment ${cmd.paymentIntentId.value} not found")
            val pendingPaymentIntent = paymentIntent.markAuthorizedPending()
        paymentIntentRepository.updatePaymentIntent(pendingPaymentIntent)
        val updated =
            when (psp.authorize(
                idempotencyKey = pendingPaymentIntent.paymentIntentId.value.toString(),
                paymentIntent=pendingPaymentIntent,
                token=cmd.paymentMethod)) {
                                            PaymentIntentStatus.AUTHORIZED -> pendingPaymentIntent.markAuthorized()
                                            PaymentIntentStatus.DECLINED   -> pendingPaymentIntent.markDeclined()
                                            else                     -> pendingPaymentIntent
        }

        paymentIntentRepository.updatePaymentIntent(updated)

        if (updated.status == PaymentIntentStatus.AUTHORIZED) {
            val outbox = toOutboxEvent(updated)
            outboxEventPort.save(outbox)
        }

        return updated
    }

    private fun toOutboxEvent(paymentIntent: PaymentIntent): OutboxEvent {
        val event = paymentOrderDomainEventMapper.toPaymentIntentAuthorizedIntentEvent(paymentIntent)

        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = EventLogContext.getTraceId(),
            data = event,
            aggregateId = paymentIntent.paymentIntentId.value.toString()
        )

        return OutboxEvent.createNew(
            oeid = paymentIntent.paymentIntentId.value,
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = serializationPort.toJson(envelope)
        )
    }
}