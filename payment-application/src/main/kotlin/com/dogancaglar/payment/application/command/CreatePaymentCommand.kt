package com.dogancaglar.payment.application.command

import com.dogancaglar.com.dogancaglar.payment.application.loggging.PaymentLogFields
import com.dogancaglar.com.dogancaglar.payment.application.port.out.SerializationPort
import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.payment.application.events.EventMetadatas
import com.dogancaglar.payment.application.mapper.PaymentOrderDomainEventMapper
import com.dogancaglar.payment.application.port.inbound.CreatePaymentUseCase
import com.dogancaglar.payment.application.port.outbound.OutboxEventPort
import com.dogancaglar.payment.domain.events.OutboxEvent
import com.dogancaglar.payment.domain.factory.PaymentFactory
import com.dogancaglar.payment.domain.model.Payment
import com.dogancaglar.payment.domain.model.PaymentOrder
import com.dogancaglar.payment.domain.model.command.CreatePaymentCommand
import com.dogancaglar.payment.domain.model.vo.PaymentId
import com.dogancaglar.payment.domain.model.vo.PaymentOrderId
import com.dogancaglar.payment.domain.port.PaymentRepository
import com.dogancaglar.payment.domain.port.id.IdGeneratorPort
import com.dogancaglar.payment.domain.port.id.IdNamespaces
import com.dogancaglar.port.PaymentOrderRepository
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.*

open class CreatePaymentCommand(
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
        val paymentOrderCreatedEvent = PaymentOrderDomainEventMapper.toPaymentOrderCreatedEvent(paymentOrder)
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            traceId = LogContext.getTraceId() ?: UUID.randomUUID().toString(),
            data = paymentOrderCreatedEvent,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
            aggregateId = paymentOrder.publicPaymentOrderId
            //if not pass parenteventid then that means its root paymentordercereatedd
        )
        val extraLogFields = mapOf(
            PaymentLogFields.PUBLIC_PAYMENT_ORDER_ID to paymentOrder.publicPaymentOrderId,
            PaymentLogFields.PUBLIC_PAYMENT_ID to paymentOrder.publicPaymentId
        )
        LogContext.with(envelope, additionalContext = extraLogFields) {
            logger.info(
                "Creating OutboxEvent for eventType={}, aggregateId={}, eventId={}",
                envelope.eventType,
                envelope.aggregateId,
                envelope.eventId
            )
        }

        return OutboxEvent.createNew(
            oeid = paymentOrder.paymentOrderId.value,
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = serializationPort.toJson(envelope),
        )
    }
}