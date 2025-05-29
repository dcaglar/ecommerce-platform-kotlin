package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.*
import com.dogancaglar.paymentservice.application.helper.PaymentFactory
import com.dogancaglar.paymentservice.application.mapper.PaymentOrderEventMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.IdGeneratorPort
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import com.dogancaglar.paymentservice.domain.port.PaymentOutboundPort
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentResponseDTO
import com.dogancaglar.paymentservice.web.mapper.PaymentRequestMapper
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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

    private val logger = LoggerFactory.getLogger(javaClass)


    private val paymentFactory: PaymentFactory = PaymentFactory(idGenerator, clock)

    @Transactional
    fun createPayment(request: PaymentRequestDTO): PaymentResponseDTO {

        //create domain
        val paymentDomain = paymentFactory.createFrom(request)
        //we already have ids for domains
        val paymentOrderList = mutableListOf<PaymentOrder>()
        // each order is represent on payment request to PSP
        for (order in paymentDomain.paymentOrders) {
            try {
                paymentOrderList.add(order);
            } catch (ex: Exception) {
                logger.warn("PSP call failed for PaymentOrder ${order.paymentOrderId}, falling back to async", ex)
            }

        }
        //save paymeetn
        paymentOutboundPort.save(paymentDomain)
        //save paymentorders
        paymentOrderOutboundPort.saveAll(paymentOrderList)
        //build and genereate outboxevent for eachpersistedd payment order domain
        val outboxBatch = buildOutboxEvents(paymentDomain.paymentOrders)
        // persist payment domain to db
        outboxEventPort.saveAll(outboxBatch);
        return PaymentRequestMapper.toResponse(paymentDomain);


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

        val jsonPayload = objectMapper.writeValueAsString(envelope)
        return OutboxEvent.createNew(
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = jsonPayload,
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

    fun processPendingPayment(order: PaymentOrder, reason: String?, error: String?): PaymentOrder {
        val updated = order.markAsPending().updatedAt(LocalDateTime.now(clock))
            .withRetryReason(reason)
            .withLastError(error)
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
        val updated = order
            .markAsFinalizedFailed()
            .withRetryReason(reason)
            .updatedAt(LocalDateTime.now(clock))
        paymentOrderOutboundPort.save(updated)
        return updated
    }

    fun fromCreatedEvent(event: PaymentOrderCreated): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = event.paymentOrderId.toLong(),
            publicPaymentOrderId = event.publicPaymentOrderId,
            paymentId = event.paymentId.toLong(),
            publicPaymentId = event.publicPaymentId,
            sellerId = event.sellerId,
            amount = Amount(event.amountValue, event.currency),
            status = PaymentOrderStatus.valueOf(event.status),
            createdAt = event.createdAt,
            updatedAt = LocalDateTime.now(), // or event.updatedAt if you trust it
            retryCount = event.retryCount
        )
    }

    fun fromRetryRequestedEvent(event: PaymentOrderRetryRequested): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = event.paymentOrderId.toLong(),
            publicPaymentOrderId = event.publicPaymentOrderId,
            paymentId = event.paymentId.toLong(),
            publicPaymentId = event.publicPaymentId,
            sellerId = event.sellerId,
            amount = Amount(event.amountValue, event.currency),
            status = PaymentOrderStatus.valueOf(event.status),
            createdAt = event.createdAt,
            updatedAt = LocalDateTime.now(), // or event.updatedAt if you trust it
            retryCount = event.retryCount
        )
    }

    fun fromDuePaymentOrderStatusCheck(event: DuePaymentOrderStatusCheck): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = event.paymentOrderId.toLong(),
            publicPaymentOrderId = event.publicPaymentOrderId,
            paymentId = event.paymentId.toLong(),
            publicPaymentId = event.publicPaymentId,
            sellerId = event.sellerId,
            amount = Amount(event.amountValue, event.currency),
            status = PaymentOrderStatus.valueOf(event.status),
            createdAt = event.createdAt,
            updatedAt = LocalDateTime.now(), // or event.updatedAt if you trust it
            retryCount = event.retryCount
        )
    }

    fun fromPaymentOrderStatusScheduled(event: PaymentOrderStatusScheduled): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = event.paymentOrderId.toLong(),
            publicPaymentOrderId = event.publicPaymentOrderId,
            paymentId = event.paymentId.toLong(),
            publicPaymentId = event.publicPaymentId,
            sellerId = event.sellerId,
            amount = Amount(event.amountValue, event.currency),
            status = PaymentOrderStatus.valueOf(event.status),
            createdAt = event.createdAt,
            updatedAt = LocalDateTime.now(), // or event.updatedAt if you trust it
            retryCount = event.retryCount
        )
    }

    fun fromScheduledPaymentOrderStatusRequest(event: ScheduledPaymentOrderStatusRequest): PaymentOrder {
        return PaymentOrder(
            paymentOrderId = event.paymentOrderId.toLong(),
            publicPaymentOrderId = event.publicPaymentOrderId,
            paymentId = event.paymentId.toLong(),
            publicPaymentId = event.publicPaymentId,
            sellerId = event.sellerId,
            amount = Amount(event.amountValue, event.currency),
            status = PaymentOrderStatus.valueOf(event.status),
            createdAt = event.createdAt,
            updatedAt = LocalDateTime.now(), // or event.updatedAt if you trust it
            retryCount = event.retryCount
        )
    }


}


@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}

