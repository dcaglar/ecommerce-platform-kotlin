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
import com.dogancaglar.paymentservice.ports.outbound.*
import org.slf4j.LoggerFactory
import java.util.*


class AuthorizePaymentService(
    private val paymentRepository: PaymentRepository,
    private val psp: PspAuthGatewayPort,
    private val outboxEventPort: OutboxEventRepository,
    private val idGeneratorPort: IdGeneratorPort,
    private val serializationPort: SerializationPort,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper) : AuthorizePaymentUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)


    override fun authorize(cmd: CreatePaymentCommand): Payment {
        val paymentId = PaymentId(idGeneratorPort.nextPaymentId(cmd.buyerId, cmd.orderId))
        val payment = Payment.createNew(
            paymentId = paymentId,
            buyerId = cmd.buyerId,
            orderId = cmd.orderId,
            totalAmount = cmd.totalAmount
        )

        // 1) persist initial PENDING_AUTH
        paymentRepository.save(payment)

        // 2) call PSP
        val pspStatus = psp.authorize(payment)

        val updated = when (pspStatus) {
            PaymentStatus.AUTHORIZED -> payment.authorize(Utc.nowLocalDateTime())
            PaymentStatus.DECLINED   -> payment.decline(Utc.nowLocalDateTime())
            PaymentStatus.PENDING_AUTH -> payment
            else -> payment
        }

        // 3) persist final status
        paymentRepository.updatePayment(updated)

        // 4) if AUTHORIZED, emit outbox
        if (updated.status == PaymentStatus.AUTHORIZED) {
            val outbox = toOutboxEvent(updated, cmd.paymentLines)
            outboxEventPort.save(outbox)
        }

        return updated
    }

    private fun toOutboxEvent(updated: Payment,paymentLines: List<PaymentLine>): OutboxEvent {
        val paymentAuthorizedEvent = paymentOrderDomainEventMapper.toPaymentAuthorized(updated,paymentLines)
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = EventLogContext.getTraceId(),
            data = paymentAuthorizedEvent,
            aggregateId = updated.paymentId.value.toString()
        )


        return OutboxEvent.createNew(
            oeid = updated.paymentId.value,
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = serializationPort.toJson(envelope),
        )
    }
}