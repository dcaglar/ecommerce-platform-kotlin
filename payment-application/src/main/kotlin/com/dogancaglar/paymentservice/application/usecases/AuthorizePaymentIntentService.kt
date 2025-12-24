package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentIntentUseCase
import com.dogancaglar.paymentservice.domain.commands.AuthorizePaymentIntentCommand
import com.dogancaglar.paymentservice.domain.exception.PaymentNotReadyException
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.PaymentMethod
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.outbound.*
import kotlin.collections.plus

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
            ?: error("PaymentIntent ${cmd.paymentIntentId.value} not found")
        // 1) Idempotent behavior first (NO domain transition before this)
        when (paymentIntent.status) {
            PaymentIntentStatus.CREATED_PENDING -> {
                // Payment not ready for authorization yet
                throw PaymentNotReadyException("Payment ${cmd.paymentIntentId.value} is not ready")
            }

            PaymentIntentStatus.AUTHORIZED,
            PaymentIntentStatus.DECLINED,
            PaymentIntentStatus.CANCELLED -> return paymentIntent

            PaymentIntentStatus.PENDING_AUTH -> return paymentIntent // controller maps to 202

            PaymentIntentStatus.CREATED -> {
                // continue
            }
        }
        // 2) Concurrency gate: only ONE request may flip CREATED -> PENDING_AUTH
        val won = paymentIntentRepository.tryMarkPendingAuth(cmd.paymentIntentId, Utc.nowInstant())
        if (!won) {
            // someone else started authorization; return latest state
            return paymentIntentRepository.findById(cmd.paymentIntentId)
                ?: error("PaymentIntent not found ${cmd.paymentIntentId.value}")
        }

        // 3) We "own" the authorization attempt; update to pending before psp call (in-memory)
        val pendingPaymentIntent = paymentIntent.markAuthorizedPending()
        //call the actual psp
        val stripeConfirmIdempotencyKey= "confirm:${paymentIntent.paymentIntentId.value}"
        // For Stripe Payment Element, paymentMethod is optional - payment method is already attached to PaymentIntent
        val pspStatus = psp.authorize(
            idempotencyKey = stripeConfirmIdempotencyKey,
            paymentIntent = pendingPaymentIntent,
            token = cmd.paymentMethod
        )
        return processPspAuthorizationResponse(pendingPaymentIntent,pspStatus)

    }


    private fun processPspAuthorizationResponse( pendingPaymentIntent: PaymentIntent, pspStatus: PaymentIntentStatus): PaymentIntent{
        var finalizedPaymentIntent : PaymentIntent?=null
        if(pspStatus == PaymentIntentStatus.AUTHORIZED){
            var finalizedPaymentIntent = pendingPaymentIntent.markAuthorized()
            val paymentId = PaymentId(idGeneratorPort.nextPaymentId(finalizedPaymentIntent.buyerId,finalizedPaymentIntent.orderId))
            //2.create payment + paymentorders + outboxevents + updated[pamyentintent
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
        } else if (pspStatus == PaymentIntentStatus.DECLINED){
            finalizedPaymentIntent = pendingPaymentIntent.markDeclined()
        } else {
           finalizedPaymentIntent = pendingPaymentIntent
        }
        return finalizedPaymentIntent!!
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