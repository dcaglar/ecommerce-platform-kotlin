package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.helper.PaymentFactory
import com.dogancaglar.paymentservice.application.mapper.PaymentOrderEventMapper
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.port.IdGeneratorPort
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import com.dogancaglar.paymentservice.domain.port.PaymentOutboundPort
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentResponseDTO
import com.dogancaglar.paymentservice.web.mapper.PaymentRequestMapper
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.util.*

@Service
class PaymentService(
    private val paymentOutboundPort: PaymentOutboundPort,
    private val paymentOrderOutboundPort: PaymentOrderOutboundPort,
    private val outboxEventPort: OutboxEventPort,
    private val idGenerator: IdGeneratorPort,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper,
    private val clock: Clock
) {

    private val paymentFactory: PaymentFactory = PaymentFactory(idGenerator, clock)

    @Transactional
    fun createPayment(request: PaymentRequestDTO): PaymentResponseDTO {

        //create domain
        val paymentDomain = paymentFactory.createFrom(request)
        //build and genereate outboxevent for eachpersistedd payment order domain
        buildOutboxEvents(paymentDomain.paymentOrders).forEach { outboxEventPort.save(it) }
        // persist payment domain to db
        paymentOutboundPort.save(paymentDomain)
        return PaymentRequestMapper.toResponse(paymentDomain)


    }

    private fun buildOutboxEvents(paymentOrders: List<PaymentOrder>): List<OutboxEvent> {
        return paymentOrders.map { toOutBoxEvent(it) }
    }

    private fun toOutBoxEvent(paymentOrder: PaymentOrder): OutboxEvent {
        val event = PaymentOrderEventMapper.toPaymentOrderCreatedEvent(paymentOrder)
        val traceId = MDC.get(LogFields.TRACE_ID) ?: UUID.randomUUID().toString()
        val envelope = EventEnvelope.wrap(
            eventType = "payment_order_created",
            aggregateId = paymentOrder.publicPaymentOrderId,
            data = event,
            traceId = traceId
        )

        val json = objectMapper.writeValueAsString(envelope)
        return OutboxEvent.createNew(
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = json,
            createdAt = LocalDateTime.now(clock),
        )
    }

    fun processSuccessfulPayment(order: PaymentOrder): PaymentOrder {
        val updated = order
            .markAsPaid()
            .updatedAt(LocalDateTime.now(clock))
        paymentOrderOutboundPort.save(updated)
        return updated
    }

    fun handleRetryAttempt(
        order: PaymentOrder,
        reason: String? = null,
        lastError: String? = null
    ): Pair<PaymentOrder, Boolean> {
        val updated = order
            .markAsFailed()
            .incrementRetry()
            .withRetryReason(reason)
            .withLastError(lastError)
            .updatedAt(LocalDateTime.now(clock))

        paymentOrderOutboundPort.save(updated)

        val exceeded = updated.retryCount >= MAX_RETRIES
        return updated to exceeded
    }

    companion object {
        private const val MAX_RETRIES = 5
    }

    fun processNonRetryableFailure(
        order: PaymentOrder,
        reason: String? = null
    ): PaymentOrder {
        return order
            .markAsFinalizedFailed()
            .withRetryReason(reason)
            .updatedAt(LocalDateTime.now(clock))
    }


}

