package com.dogancaglar.paymentservice.application.service

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.event.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.helper.PaymentFactory
import com.dogancaglar.paymentservice.application.helper.PaymentOrderFactory
import com.dogancaglar.paymentservice.application.mapper.PaymentOrderEventMapper
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrderStatusCheck
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.*
import com.dogancaglar.paymentservice.psp.PSPStatusMapper
import com.dogancaglar.paymentservice.web.dto.PaymentRequestDTO
import com.dogancaglar.paymentservice.web.dto.PaymentResponseDTO
import com.dogancaglar.paymentservice.web.mapper.PaymentRequestMapper
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.min
import kotlin.math.pow

@Service
class PaymentService(
    @Qualifier("paymentRetryQueueAdapter") // <-- matches your @Component name
    private val retryQueuePort: RetryQueuePort<PaymentOrderRetryRequested>,
    val paymentEventPublisher: PaymentEventPublisher,
    private val paymentOutboundPort: PaymentOutboundPort,
    private val paymentOrderOutboundPort: PaymentOrderOutboundPort,
    private val outboxEventPort: OutboxEventPort,
    private val statusCheckOutBoundPort: PaymentOrderStatusCheckOutBoundPort,
    private val idGenerator: IdGeneratorPort,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper,
    private val clock: Clock
) {

    companion object {
        const val MAX_RETRIES = 5
    }

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
            traceId = LogContext.getTraceId() ?: UUID.randomUUID().toString(),
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
                envelope.eventType,
                envelope.aggregateId,
                envelope.eventId
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


    fun processPspResult(event: PaymentOrderEvent, pspStatus: PaymentOrderStatus) {
        val order = paymentOrderFactory.fromEvent(event)
        when {
            pspStatus == PaymentOrderStatus.SUCCESSFUL -> {

                handleSuccessfulPayment(order = order)
            }

            PSPStatusMapper.requiresRetryPayment(pspStatus) -> {
                handleRetryEvent(order = order, reason = pspStatus.name)
            }

            PSPStatusMapper.requiresStatusCheck(pspStatus) -> {
                handlePaymentStatusCheckEvent(order)
            }

            else -> {
                handleNonRetryableFailEvent(order)
            }
        }
        // All event publishing, retry logic, and DB writes live here.
    }


    fun handleSuccessfulPayment(order: PaymentOrder) {
        val updatedOrder = order.markAsPaid().withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderOutboundPort.save(updatedOrder)
        paymentEventPublisher.publish(
            eventMetaData = EventMetadatas.PaymentOrderSuccededMetaData,
            aggregateId = updatedOrder.publicPaymentOrderId,
            data = PaymentOrderEventMapper.toPaymentOrderSuccededEvent(updatedOrder)
        )
    }

    fun handlePaymentStatusCheckEvent(
        order: PaymentOrder, reason: String? = null, lastError: String? = null
    ) {
        val updated = order.markAsPending().incrementRetry().withRetryReason(reason).withLastError(lastError)
            .withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderOutboundPort.save(updated)

        statusCheckOutBoundPort.save(
            PaymentOrderStatusCheck.createNew(
                updated.paymentOrderId, LocalDateTime.now(clock).plusMinutes(30)
            )
        )
    }


    fun handleRetryEvent(
        order: PaymentOrder, reason: String? = null, lastError: String? = null
    ) {

        val retryCount = retryQueuePort.getRetryCount(order.paymentOrderId)
        val nextRetryCount = retryCount + 1
        val updated = order.markAsFailed().withRetryReason(reason).withLastError(lastError)
            .withUpdatedAt(LocalDateTime.now(clock))

        if (nextRetryCount < MAX_RETRIES) {
            val backOffExpMillis = computeEqualJitterBackoff(attempt = nextRetryCount)
            val scheduledAt = System.currentTimeMillis().plus(backOffExpMillis)
            LogContext.withRetryFields(
                retryCount = nextRetryCount, retryReason = reason, lastErrorMessage = lastError,
                backOffInMillis = backOffExpMillis
            ) {
                logRetrySchedule(order, nextRetryCount, scheduledAt, reason, lastError)
            }
            retryQueuePort.scheduleRetry(order, retryReason = reason, backOffMillis = backOffExpMillis)
        } else {
            logger.warn(
                "[RETRY-FAILURE] paymentOrderId={} has reached the maximum retry attempts ({}/{}). Marking as permanently FAILED. LastError='{}', RetryReason='{}'",
                order.publicPaymentOrderId,
                nextRetryCount,
                MAX_RETRIES,
                lastError ?: "-",
                reason ?: "-"
            )
            // ✅ Reset the retry counter after exceeding max retries
            retryQueuePort.resetRetryCounter(order.paymentOrderId)
            //finalize the order
            handleNonRetryableFailEvent(updated)

        }
    }

    private fun logRetrySchedule(
        order: PaymentOrder,
        nextRetryCount: Int,
        scheduledAt: Long,
        reason: String?,
        lastError: String?
    ) {
        val scheduledLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAt), clock.zone)
        val formattedLocal = scheduledLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        val scheduledUtc = LocalDateTime.ofInstant(Instant.ofEpochMilli(scheduledAt), ZoneId.of("UTC"))
        val formattedUtc = scheduledUtc.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
        logger.info(
            "Scheduling retry for paymentOrderId={} [attempt {}/{}] due at {} (local, {}) / {} (UTC). Reason='{}', LastError='{}'",
            order.publicPaymentOrderId,
            nextRetryCount,
            MAX_RETRIES,
            formattedLocal,          // Local time
            clock.zone,              // What is 'local'
            formattedUtc,            // UTC time
            reason ?: "",
            lastError ?: ""
        )
    }

    fun handleNonRetryableFailEvent(
        order: PaymentOrder, reason: String? = null
    ): PaymentOrder {
        val updated = order.markAsFinalizedFailed().withRetryReason(reason).withUpdatedAt(LocalDateTime.now(clock))
        paymentOrderOutboundPort.save(updated)
        logger.info(
            "PaymentOrder {} marked as permanently FAILED. Reason='{}'",
            updated.publicPaymentOrderId, (reason ?: updated.retryReason ?: "N/A")
        )
        return updated
    }


    fun mapEventToDomain(event: PaymentOrderEvent): PaymentOrder {
        return paymentOrderFactory.fromEvent(event)
    }


    /**
     * Calculates backoff delay using AWS "Equal Jitter" strategy.
     * Each retry waits at least half of the exponential backoff, up to maxDelay.
     *
     * @param attempt      1-based retry attempt number (first retry = 1)
     * @param minDelayMs   Minimum delay (e.g., 2000L for 2 seconds)
     * @param maxDelayMs   Maximum delay (e.g., 300000L for 5 minutes)
     * @param random       Random instance (for testability)
     * @return             Milliseconds to wait before next retry
     */
    fun computeEqualJitterBackoff(
        attempt: Int,
        minDelayMs: Long = 2_000L,
        maxDelayMs: Long = 300_000L,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): Long {
        require(attempt >= 1) { "Attempt must be >= 1" }
        val exp = minDelayMs * 2.0.pow(attempt - 1).toLong()
        val capped = min(exp, maxDelayMs)
        val half = capped / 2
        return half + random.nextLong(half + 1) // [half, capped]
    }

}


@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}

