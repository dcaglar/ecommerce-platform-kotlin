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
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentUseCase
import com.dogancaglar.paymentservice.ports.outbound.*
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime
import java.util.*


class AuthorizePaymentService(
    private val paymentRepository: PaymentRepository,
    private val psp: PspAuthGatewayPort,
    private val outboxEventPort: OutboxEventRepository,
    private val idGeneratorPort: IdGeneratorPort,
    private val serializationPort: SerializationPort,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
    private val clock : Clock
) : AuthorizePaymentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)


    override fun authorize(cmd: CreatePaymentCommand): Payment {
        // 1️⃣ Create domain aggregate (Payment)
        val paymentId = PaymentId(idGeneratorPort.nextPaymentId(cmd.buyerId,cmd.orderId))
        val payment = Payment.createNew(
            paymentId = paymentId,
            buyerId = cmd.buyerId,
            orderId = cmd.orderId,
            totalAmount = cmd.totalAmount,
            clock = clock
        )

            // 2️⃣ Persist intent (PENDING_AUTH)
        val persisted =paymentRepository.saveIdempotent(payment)
        if(persisted.status!= PaymentStatus.PENDING_AUTH){
            logger.info("🌀 Idempotent replay detected for key={}, returning existing paymentId={}", persisted.idempotencyKey, persisted.paymentId.value)
            return persisted
        }

        // 3️⃣ Perform PSP authorization use odempotency in header.
        val pspStatus = psp.authorize(payment)

        // 4️⃣ Update domain aggregate based on PSP result
        val updated = when (pspStatus) {
            PaymentStatus.AUTHORIZED -> payment.authorize(LocalDateTime.now(clock))
            PaymentStatus.DECLINED -> payment.decline(LocalDateTime.now(clock))
            PaymentStatus.PENDING_AUTH -> payment
            else -> payment
        }

        // 5️⃣ On success: persist outcome + emit Outbox<PaymentPipelineAuthorized>
        if (updated.status == PaymentStatus.AUTHORIZED) {
            paymentRepository.updatePayment(updated)
            val paymentAuthorized = toOutboxEvent(updated,cmd.paymentLines)
            outboxEventPort.save(paymentAuthorized)
        } else {
            paymentRepository.updatePayment(updated)
        }

        return updated
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
            createdAt = updated.createdAt
        )
    }
}