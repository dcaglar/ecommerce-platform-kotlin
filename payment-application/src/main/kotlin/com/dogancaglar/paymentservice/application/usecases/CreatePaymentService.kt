package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.constants.IdNamespaces
import com.dogancaglar.paymentservice.application.constants.PaymentLogFields
import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.events.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.util.PaymentFactory
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import org.slf4j.LoggerFactory
import paymentservice.port.outbound.IdGeneratorPort
import java.time.Clock
import java.util.*

open class CreatePaymentService(
    private val idGeneratorPort: IdGeneratorPort,
    private val paymentRepository: PaymentRepository,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val outboxEventPort: OutboxEventPort,
    private val serializationPort: SerializationPort,
    private val clock: Clock,
) : CreatePaymentUseCase {

    val logger = LoggerFactory.getLogger(javaClass)

    open override fun create(command: CreatePaymentCommand): Payment {
        val paymentOrderIdList = command.paymentLines.map {
            PaymentOrderId(idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER))
        }
        val paymentId = getNextPaymentId()
        val payment = PaymentFactory(clock).createPayment(command, paymentId, paymentOrderIdList)

        paymentRepository.save(payment)
        paymentOrderRepository.upsertAll(payment.paymentOrders)

        val outboxBatch = payment.paymentOrders.map { toOutBoxEvent(it) }
        outboxEventPort.saveAll(outboxBatch)

        return payment
    }

    private fun getNextPaymentOrderId(): PaymentOrderId {
        val nextId = idGeneratorPort.nextId(IdNamespaces.PAYMENT_ORDER)
        return PaymentOrderId(nextId)
    }

    private fun getNextPaymentId(): PaymentId {
        val nextId = idGeneratorPort.nextId(IdNamespaces.PAYMENT)
        return PaymentId(nextId)
    }


    private fun toOutBoxEvent(paymentOrder: PaymentOrder): OutboxEvent {
        val paymentOrderCreatedEvent = PaymentOrderDomainEventMapper.toPaymentOrderCreatedEvent(order = paymentOrder)
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            traceId = LogContext.getTraceId() ?: UUID.randomUUID().toString(),
            data = paymentOrderCreatedEvent,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
            aggregateId = paymentOrderCreatedEvent.paymentOrderId
            //if not pass parenteventid then that means its root paymentordercereatedd
        )
        val extraLogFields = mapOf(
            PaymentLogFields.PUBLIC_PAYMENT_ORDER_ID to paymentOrderCreatedEvent.publicPaymentOrderId,
            PaymentLogFields.PUBLIC_PAYMENT_ID to paymentOrderCreatedEvent.publicPaymentId
        )
        LogContext.with(envelope, additionalContext = extraLogFields) {
            logger.info(
                "Creating OutboxEvent for eventType={}, aggregateId={}, eventId={}",
                envelope.eventType,
                envelope.aggregateId,
                envelope.eventId
            )
        }

        return OutboxEvent.Companion.createNew(
            oeid = paymentOrder.paymentOrderId.value,
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = serializationPort.toJson(envelope),
            clock = clock,
        )
    }
}