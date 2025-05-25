package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.redis.RedisIdKeys
import com.dogancaglar.paymentservice.application.helper.DomainIdGenerator
import com.dogancaglar.paymentservice.domain.event.mapper.toCreatedEvent
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.OutboxEventRepository
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import com.dogancaglar.paymentservice.domain.port.PaymentOutBoundPort
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.mapper.AmountMapper
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
class PaymentService(
    private val paymentOutBoundPort: PaymentOutBoundPort,
    private val paymentOrderOutboundPort: PaymentOrderOutboundPort,
    private val outboxEventRepository: OutboxEventRepository,
    private val idGenerator: DomainIdGenerator,
    @Qualifier("myObjectMapper")
    private val objectMapper: ObjectMapper,
    private val clock: Clock
    private paymentFactory: PaymentFactory
) {

    private val factory = PaymentFactory(idGenerator, clock)
    val payment = factory.createFrom(request)
    @Transactional
    fun createPayment(request: PaymentRequestDTO) {

    }

    private fun buildOutboxEvents(paymentOrders: List<PaymentOrder>): List<OutboxEvent> {
        return paymentOrders.map { toOutBoxEvent(it) }
    }

    private fun toOutBoxEvent(paymentOrder: PaymentOrder): OutboxEvent {
        val event = paymentOrder.toCreatedEvent()
        val traceId = MDC.get(LogFields.TRACE_ID) ?: UUID.randomUUID().toString()

        logger.info("Generate outbox event paymentOrderId=${'$'}{paymentOrder.paymentOrderId}, traceId=$traceId")

        val envelope = EventEnvelope.wrap(
            eventType = "payment_order_created",
            aggregateId = paymentOrder.paymentOrderId,
            data = event,
            traceId = traceId
        )

        val json = objectMapper.writeValueAsString(envelope)

        return OutboxEvent(
            eventId = envelope.eventId,
            eventType = "payment_order_created",
            aggregateId = envelope.aggregateId,
            payload = json,
            createdAt = LocalDateTime.now(clock),
            status = "NEW"
        )
    }

    class PaymentFactory(
        private val idGenerator: DomainIdGenerator,
        private val clock: Clock
    ) {
        private fun createFrom(request: PaymentRequestDTO): Payment {
            val now = LocalDateTime.now(clock)

            val (paymentId, paymentPublicId) = idGenerator.nextId(RedisIdKeys.PAYMENT_ID)
            val paymentOrderIds = List(request.paymentOrders.size) {
                idGenerator.nextId(RedisIdKeys.PAYMENT_ORDER_ID)
            }

            val paymentOrders = request.paymentOrders.mapIndexed { index, dto ->
                val (orderId, orderPublicId) = paymentOrderIds[index]
                PaymentOrder(
                    paymentOrderId = orderId,
                    publicId = orderPublicId,
                    sellerId = dto.sellerId,
                    amount = AmountMapper.toDomain(dto.amount),
                    status = PaymentOrderStatus.INITIATED,
                    createdAt = now,
                    updatedAt = now
                )
            }

            return Payment(
                paymentId = paymentId,
                publicId = paymentPublicId,
                buyerId = request.buyerId,
                orderId = request.orderId,
                totalAmount = AmountMapper.toDomain(request.totalAmount),
                status = PaymentStatus.INITIATED,
                createdAt = now,
                updatedAt = now,
                paymentOrders = paymentOrders
            )
        }
    }
}
