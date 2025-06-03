package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.event.*
import com.dogancaglar.paymentservice.application.helper.PaymentFactory
import com.dogancaglar.paymentservice.application.helper.PaymentOrderFactory
import com.dogancaglar.paymentservice.application.mapper.PaymentOrderEventMapper
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrderStatusCheck
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.IdGeneratorPort
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import com.dogancaglar.paymentservice.domain.port.PaymentOrderStatusCheckOutBoundPort
import com.dogancaglar.paymentservice.domain.port.PaymentOutboundPort
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.dogancaglar.paymentservice.psp.PSPStatusMapper
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentResponseDTO
import com.dogancaglar.paymentservice.web.mapper.PaymentRequestMapper
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.UUID
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.math.pow

@Service
class PaymentService(
    @Qualifier("paymentRetryPaymentAdapter") val paymentRetryPaymentAdapter: RetryQueuePort<PaymentOrderRetryRequested>,
    val paymentEventPublisher: PaymentEventPublisher,
    private val paymentOutboundPort: PaymentOutboundPort,
    private val paymentOrderOutboundPort: PaymentOrderOutboundPort,
    private val outboxEventPort: OutboxEventPort,
    private val statusCheckOutBoundPort: PaymentOrderStatusCheckOutBoundPort,
    private val idGenerator: IdGeneratorPort,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper,
    private val clock: Clock
) {

    private val logger = LoggerFactory.getLogger(javaClass)


    private val paymentFactory: PaymentFactory = PaymentFactory(idGenerator, clock)

    private val paymentOrderFactory: PaymentOrderFactory = PaymentOrderFactory()


    @Transactional
    fun createPayment(request: PaymentRequestDTO): PaymentResponseDTO {
        //create domain
        val paymentDomain = paymentFactory.createFrom(request)
        //we already have ids for domains
        val paymentOrderList = mutableListOf<PaymentOrder>()
        // each order is represent on payment request to PSP
        for (order in paymentDomain.paymentOrders) {
            try {
                paymentOrderList.add(order)
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
        outboxEventPort.saveAll(outboxBatch)
        return PaymentRequestMapper.toResponse(paymentDomain)


    }


    private fun buildOutboxEvents(paymentOrders: List<PaymentOrder>): List<OutboxEvent> {
        return paymentOrders.map { toOutBoxEvent(it) }
    }

    private fun toOutBoxEvent(paymentOrder: PaymentOrder): OutboxEvent {
        val paymentOrderCreatedEvent = PaymentOrderEventMapper.toPaymentOrderCreatedEvent(paymentOrder)
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            traceId = LogContext.getTraceId()
                ?: UUID.randomUUID().toString(),
            data = paymentOrderCreatedEvent,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
            aggregateId = paymentOrder.publicPaymentOrderId
            //if not pass parenteventid then that means its root paymentordercereatedd
        )
        val extraLogFields = mapOf(
            LogFields.PUBLIC_PAYMENT_ORDER_ID to paymentOrder.publicPaymentOrderId,
            LogFields.PUBLIC_PAYMENT_ID to paymentOrder.publicPaymentId
        )
        LogContext.with(envelope, additionalContext = extraLogFields) {
            logger.info(
                "Creating OutboxEvent for eventType={}, aggregateId={}, eventId={}",
                envelope.eventType, envelope.aggregateId, envelope.eventId
            )
        }
        val jsonPayload = objectMapper.writeValueAsString(envelope)
        return OutboxEvent.createNew(
            eventType = envelope.eventType,
            aggregateId = envelope.aggregateId,
            payload = jsonPayload,
            createdAt = LocalDateTime.now(clock),
        )
    }

    fun processPspResult(event: PaymentOrderEvent, pspStatus: PaymentOrderStatus,parentEventId: UUID) {
        val order = paymentOrderFactory.fromEvent(event)
        when {
            pspStatus == PaymentOrderStatus.SUCCESSFUL -> {

              processSuccessfulPayment(order = order)
            }
            PSPStatusMapper.requiresRetryPayment(pspStatus) -> {
                 handleRetryAttempt(order =order )
            }
            PSPStatusMapper.requiresStatusCheck(pspStatus) -> {
                val updatedOrder = order.markAsPending().incrementRetry().withUpdatedAt(LocalDateTime.now(clock))
                val paymentOrderStatusScheduled = PaymentOrderEventMapper.toPaymentOrderStatusCheckRequested(updatedOrder)
                paymentOrderOutboundPort.save(updatedOrder)
                statusCheckOutBoundPort.save(
                    PaymentOrderStatusCheck.createNew(updatedOrder.paymentOrderId, LocalDateTime.now(clock).plusMinutes(30))
                )
                paymentEventPublisher.publish(
                    event = EventMetadatas.PaymentOrderStatusCheckScheduledMetadata,
                    aggregateId = updatedOrder.publicPaymentOrderId,
                    data = paymentOrderStatusScheduled,
                )
                //paymentStatusCheckQueuePort.savePaymentStatusRequest()
                //persist a entry in paymentstatuscheck table for a time
            }
            else -> {
                val failedOrder = order.markAsFinalizedFailed().incrementRetry().
                withUpdatedAt(LocalDateTime.now(clock))
                    .withLastError(error = event.lastErrorMessage)
                    .withRetryReason(reason = event.retryReason)
                paymentOrderOutboundPort.save(failedOrder)
                //todo publish a payment_failed , so  intertsted domains would use that
            }
        }
        // All event publishing, retry logic, and DB writes live here.
    }


    fun processSuccessfulPayment(order: PaymentOrder) {
        val updatedOrder = order.markAsPaid().withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderOutboundPort.save(updatedOrder)
        paymentEventPublisher.publish(
            event = EventMetadatas.PaymentOrderSuccededMetaData,
            aggregateId = updatedOrder.publicPaymentOrderId,
            data = PaymentOrderEventMapper.toPaymentOrderSuccededEvent(updatedOrder))
    }

    fun processPendingPayment(order: PaymentOrder, reason: String?, error: String?): PaymentOrder {
        val updated = order.markAsPending().withUpdatedAt(LocalDateTime.now(clock))
            .withRetryReason(reason)
            .withLastError(error)
        paymentOrderOutboundPort.save(updated)
        return updated
    }

    fun handleRetryAttempt(
        order: PaymentOrder,
        reason: String? = null,
        lastError: String? = null
    ) {
        val updated = order
            .markAsFailed()
            .incrementRetry()
            .withRetryReason(reason)
            .withLastError(lastError)
            .withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderOutboundPort.save(updated)
        if(updated.retryCount< MAX_RETRIES){
            val backOffExpMillis = computeBackoffDelayMillis(retryCount = updated.retryCount)
            paymentRetryPaymentAdapter.scheduleRetry(paymentOrder = updated, backOffMillis = backOffExpMillis)
        } else {
            val finalizedStatus = updated.markAsFinalizedFailed()
            paymentOrderOutboundPort.save(finalizedStatus)


        }

    }

    companion object {
        private const val MAX_RETRIES = 5
    }

    fun handleNonRetryableFailure(
        order: PaymentOrder,
        reason: String? = null
    ): PaymentOrder {
        val updated = order
            .markAsFinalizedFailed()
            .withRetryReason(reason)
            .withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderOutboundPort.save(updated)
        return updated
    }

    fun mapEventToDomain(event: PaymentOrderEvent): PaymentOrder {
        return paymentOrderFactory.fromEvent(event)
    }
    fun calculateBackoffMillis(retryCount: Long): Long {
        val baseDelay = 5_000L // 5 seconds
        return baseDelay * (retryCount + 1) // Linear or exponential backoff
    }

    /**
     * Returns a randomized backoff delay in milliseconds using bounded exponential backoff with full jitter.
     *
     * @param retryCount Retry attempt number (1-based).
     * @param baseDelayMillis Initial delay in milliseconds (e.g. 500).
     * @param maxDelayMillis Maximum backoff delay (e.g. 30000).
     * @param random Random instance (can be injected for tests).
     * @return Delay in milliseconds between 0 and the calculated backoff.
     *By combining exponential delay + randomization, you spread retries over time and avoid cascading failures.
     * If you use base = 500ms, maxDelay = 30s:
     *Attempt|Raw Delay|With Full Jitter (Random between 0 and Delay)
     *  1 | 500 |778
     *  2| 1 | 733
     *  3 |2 | 1.4
     */
    fun computeBackoffDelayMillis(
        retryCount: Int,
        baseDelayMillis: Long = 500,
        maxDelayMillis: Long = 30_000,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): Long {
        require(retryCount >= 1) { "attempt must be >= 1" }

        val exponential = baseDelayMillis * 2.0.pow(retryCount - 1).toLong()
        val cappedDelay = min(exponential, maxDelayMillis)

        return random.nextLong(0, cappedDelay + 1) // +1 to make it inclusive
    }

}


@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}

