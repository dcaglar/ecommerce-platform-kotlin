package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.command.AuthorizePaymentIntentCommand
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventEntityMapper
import com.dogancaglar.paymentservice.domain.exception.PaymentNotReadyException
import com.dogancaglar.paymentservice.domain.exception.PspPermanentException
import com.dogancaglar.paymentservice.domain.exception.PspTransientException
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.inbound.usecases.AuthorizePaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.outbound.*
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionException
import java.util.concurrent.RejectedExecutionException

class AuthorizePaymentIntentService(
    private val idGeneratorPort: IdGeneratorPort,
    private val paymentIntentRepository: PaymentIntentRepository,
    private val pspAuthGatewayPort: PspAuthorizationGatewayPort,
    private val resilientExecutionPort: ResilientExecutionPort,
    private val serializationPort: SerializationPort,
    private val paymentTransactionalFacadePort: PaymentTransactionalFacadePort
) : AuthorizePaymentIntentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)
    override fun authorize(cmd: AuthorizePaymentIntentCommand): PaymentIntent {
        logger.info("AuthorizePaymentIntentService.authorize  started")
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
        val now = Utc.nowInstant()
        val won = paymentIntentRepository.tryMarkPendingAuth(cmd.paymentIntentId, now)
        if (!won) {
            // someone else started authorization; return latest state
            return paymentIntentRepository.findById(cmd.paymentIntentId)
                ?: error("PaymentIntent not found ${cmd.paymentIntentId.value}")
        }

        // 3) We "own" the authorization attempt; update in-memory state before psp call
        val authPendingPaymentIntent = paymentIntent.markAuthorizedPending(Utc.fromInstant(now))
        // No redundant DB update here - tryMarkPendingAuth already secured the state in the database

        //call the actual psp
        // For Stripe Payment Element, paymentMethod is optional - payment method is already attached to PaymentIntent
        return try {
            val startPspCall = System.currentTimeMillis()
            logger.info("📡 [AuthorizeService] Initiating resilient PSP authorization for ${cmd.paymentIntentId.value}")
            val finalPaymentIntent = resilientExecutionPort.executeWithTimeoutAndBackgroundFallback(
                primaryTask = pspAuthGatewayPort.authorizePaymentIntent(authPendingPaymentIntent,cmd.paymentMethod), // tsk to be run aysnc by thread pool passed
                timeoutMs = 3000,
                onTimeoutFallback = {
                    logger.warn(
                        "Payment authorization timed out for {}, returning PENDING_AUTH and continuing in background",
                        paymentIntent.paymentIntentId.value
                    )
                    authPendingPaymentIntent // Returns PENDING_AUTH status, mapping to 202 in controller
                },
                onBackgroundSuccess = { resultFromCallStripeApi ->
                    handleBackgroundPaymentIntentAuthorizationSuccess(
                        resultFromCallStripeApi
                    )
                },
                onBackgroundFailure = { error -> handleBackgroundFailure(paymentIntent, error) }
            )
            val finishPspCall = System.currentTimeMillis()
            logger.info("Authorize took {} ms", finishPspCall - startPspCall)
            if (finalPaymentIntent.status == PaymentIntentStatus.AUTHORIZED) {
                generatePaymentOrderLines(finalPaymentIntent)
            }
            finalPaymentIntent
        } catch (e: Exception){
            handleImmediateFailure(paymentIntent,e)
        }
    }



    private fun handleBackgroundPaymentIntentAuthorizationSuccess(authorizedPaymentIntent: PaymentIntent) {
        logger.info("Background payment intent authorization successful for ${authorizedPaymentIntent.paymentIntentId.value}, promoting to status authorized and generating orders")
        generatePaymentOrderLines(authorizedPaymentIntent)
    }


    private fun handleBackgroundFailure(paymentIntent: PaymentIntent, error: Throwable) {
        logger.error("Background payment intent authorization failed for ${paymentIntent.paymentIntentId.value} , mark as declined", error)
        if (error is PspPermanentException || error !is PspTransientException) {
            //decline it
            val canceledPaymentIntent = paymentIntent.markDeclined()
            val startUpdate = System.currentTimeMillis()
            paymentIntentRepository.updatePaymentIntent(canceledPaymentIntent)
            val finishUpdate = System.currentTimeMillis()
            logger.info("db.updatePaymentIntent (failure) took {} ms", finishUpdate - startUpdate)
        }
    }

    private fun handleImmediateFailure(paymentIntent: PaymentIntent, error: Throwable): PaymentIntent {
        logger.error("Immediate exceptiuon occurred  :$error")
        val cause = if (error is CompletionException) error.cause ?: error else error
        return when (cause) {
            is PspTransientException -> {
                logger.warn("Transient failure during payment authorization for ${paymentIntent.paymentIntentId.value} : ${cause.message} ")
                paymentIntent
            }
            is RejectedExecutionException -> {
                logger.warn("PSP thread pool saturated for ${paymentIntent.paymentIntentId.value}, returning pending status (backpressure)")
                paymentIntent // Return CREATED_PENDING status, mapping to 202 in controller
            }
            is PspPermanentException -> {
                logger.error("Permanent failure during payment intent authorization for ${paymentIntent.paymentIntentId.value} , marking as DECLINED", cause)
                val cancelled = paymentIntent.markDeclined()
                val startUpdate = System.currentTimeMillis()
                paymentIntentRepository.updatePaymentIntent(cancelled)
                val finishUpdate = System.currentTimeMillis()
                logger.info("db.updatePaymentIntent (immediate failure) took {} ms", finishUpdate - startUpdate)
                cancelled
            }
            else -> {
                logger.error("Unexpected failure during payment intent authoirzation for {}", paymentIntent.paymentIntentId.value, cause)
                throw RuntimeException("Unexpected failure", cause)
            }
        }
    }




    private fun generatePaymentOrderLines(confirmedPaymentIntent : PaymentIntent){
        if(confirmedPaymentIntent.status == PaymentIntentStatus.AUTHORIZED){
            val paymentId = PaymentId(idGeneratorPort.nextPaymentId())
            //2.create payment + paymentorders + outboxevents + updated[pamyentintent
            val payment = Payment.fromAuthorizedIntent(paymentId,confirmedPaymentIntent)
            val paymentOrders = confirmedPaymentIntent.paymentOrderLines.map { line ->
                val sellerId = line.sellerId
                PaymentOrder.createNew(
                    paymentOrderId = PaymentOrderId(idGeneratorPort.nextPaymentOrderId()),
                    paymentId = paymentId,
                    sellerId = sellerId,
                    amount = line.amount
                )
            }
            //generate outbox<paymentauthorized> + outbox<paymentordercreated>  from payment objefct which is just created,and save it in db
            val outboxEventPaymentAuthorizedEvent = toOutboxPaymentAuthorizedEvent(payment)
            val outboxEventPaymentOrderCreatedList = paymentOrders.map { toOutboxPaymentOrderCreatedEvent(it) }
            //persist all changes in on tatomic tranascation
            paymentTransactionalFacadePort.handleAuthorized(confirmedPaymentIntent,payment,paymentOrders,outboxEventPaymentOrderCreatedList+outboxEventPaymentAuthorizedEvent)
        }
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
        val paymentOrderCreatedEvent = PaymentOrderDomainEventEntityMapper.toPaymentOrderCreated(paymentOrder)
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
        val event = PaymentOrderDomainEventEntityMapper.toPaymentIntentAuthorizedIntentEvent(paymentIntent)

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