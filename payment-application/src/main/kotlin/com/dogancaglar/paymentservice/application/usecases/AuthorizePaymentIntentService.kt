package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentIntentUseCase
import com.dogancaglar.paymentservice.domain.commands.AuthorizePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.outbound.*

class AuthorizePaymentIntentService(
    private val idGeneratorPort: IdGeneratorPort,
    private val paymentIntentRepository: PaymentIntentRepository,
    private val psp: PspAuthGatewayPort,
    private val serializationPort: SerializationPort,
    private val paymentTransactionalFacadePort: PaymentTransactionalFacadePort,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
) : AuthorizePaymentIntentUseCase {

    override fun authorize(cmd: AuthorizePaymentIntentCommand): PaymentIntent {
        val paymentIntent = paymentIntentRepository.findById(cmd.paymentIntentId)
            ?: error("Payment ${cmd.paymentIntentId.value} not found")
        //1-just before callling psp gateway update paymentintentstatus to authoirzed pending
            val updatedPaymentIntent = paymentIntent.markAuthorizedPending()
            paymentIntentRepository.updatePaymentIntent(updatedPaymentIntent);
        var finalizedPaymentIntent: PaymentIntent? = null
        val updated = psp.authorize(
                        idempotencyKey = updatedPaymentIntent.paymentIntentId.value.toString(),
                        paymentIntent=updatedPaymentIntent,
                        token=cmd.paymentMethod)
                if(updated == PaymentIntentStatus.AUTHORIZED){
                    //2.create payment + paymentorders + outboxevents + updated[pamyentintent
                     finalizedPaymentIntent = updatedPaymentIntent.markAuthorized()
                    val paymentId = PaymentId(idGeneratorPort.nextPaymentId(finalizedPaymentIntent.buyerId,finalizedPaymentIntent.orderId))
                    val payment = Payment.fromAuthorizedIntent(paymentId,finalizedPaymentIntent)
                    val paymentOrders = finalizedPaymentIntent.paymentOrderLines.map { line ->
                        val sellerId = line.sellerId
                        PaymentOrder.createNew(
                            paymentOrderId = PaymentOrderId(idGeneratorPort.nextPaymentOrderId(sellerId)),
                            paymentId = paymentId,
                            sellerId = sellerId,
                            amount = line.amount
                        )
                    }
                    //generate outbox<paymentauthorized> + outbox<paymentordercreated>  from payment objefct which is just created,and save it in db
                    val outboxEventPaymentAuthorizedEvent = toOutboxPaymentAuthorizedEvent(payment)
                    val outboxEventPaymentOrderCreatedList = paymentOrders.map { toOutboxPaymentOrderCreatedEvent(it) }
                    //persist all changes in on tatomic tranascation
                    paymentTransactionalFacadePort.handleAuthorized(finalizedPaymentIntent,payment,paymentOrders,outboxEventPaymentOrderCreatedList+outboxEventPaymentAuthorizedEvent)
                } else if (updated == PaymentIntentStatus.DECLINED){
                     finalizedPaymentIntent = updatedPaymentIntent.markDeclined()
                } else {
                    //todo maybe retry with back off jitter?
                }
            paymentIntentRepository.updatePaymentIntent(finalizedPaymentIntent!!);
        return finalizedPaymentIntent
    }


    private fun toOutboxPaymentAuthorizedEvent(payment: Payment): OutboxEvent {
        val paymentCreatedEvent = PaymentAuthorized.from(payment,Utc.nowInstant())
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = EventLogContext.getTraceId(),
            data = paymentCreatedEvent,
            aggregateId = paymentCreatedEvent.paymentId,
            parentEventId = EventLogContext.getEventId()
        )

        return OutboxEvent.createNew(
            oeid = payment.paymentId.value,
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = serializationPort.toJson(envelope),
        )
    }

    private fun toOutboxPaymentOrderCreatedEvent(paymentOrder: PaymentOrder): OutboxEvent {
        val paymentOrderCreatedEvent = paymentOrderDomainEventMapper.toPaymentOrderCreated(paymentOrder)
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = EventLogContext.getTraceId(),
            data = paymentOrderCreatedEvent,
            aggregateId = paymentOrderCreatedEvent.paymentOrderId,
            parentEventId = EventLogContext.getEventId()
        )

        return OutboxEvent.createNew(
            oeid = paymentOrder.paymentOrderId.value,
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = serializationPort.toJson(envelope),
        )
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