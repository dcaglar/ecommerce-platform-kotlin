package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PSPClientPort
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import jakarta.transaction.Transactional
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderRetryCommandExecutor(
    private val paymentService: PaymentService,
    val pspClient: PSPClientPort,
    val retryMetrics: RetryMetrics
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderRetryRequested>>) {
        val envelope = record.value()
        val paymentOrderCreatedEvent = envelope.data
        val order = paymentService.mapEventToDomain(paymentOrderCreatedEvent)
        LogContext.with(
            envelope, mapOf(
                LogFields.TOPIC_NAME to record.topic(),
                LogFields.CONSUMER_GROUP to "PAYMENT_RETRY_EXECUTOR",
                LogFields.PUBLIC_PAYMENT_ID to envelope.data.publicPaymentId,
                LogFields.PUBLIC_PAYMENT_ORDER_ID to envelope.aggregateId,
                LogFields.EVENT_ID to envelope.eventId.toString(),
                LogFields.TRACE_ID to envelope.traceId,
                LogFields.PARENT_EVENT_ID to envelope.parentEventId.toString()
            )
        ) {
            try {
                val response = safePspCall(order)
                logger.info("✅ PSP call PaymentOrderRetryCommandExecutor returned status=$response for paymentOrderId=${order.paymentOrderId}")
                paymentService.processPspResult(
                    event = paymentOrderCreatedEvent,
                    pspStatus = response
                )
            } catch (e: TimeoutException) {
                logger.error(
                    "⏱️ PSP call PaymentOrderRetryCommandExecutor timed out for orderId=${order.paymentOrderId}, retrying...",
                    e
                )
                paymentService.processPspResult(
                    event = paymentOrderCreatedEvent,
                    pspStatus = PaymentOrderStatus.TIMEOUT
                )
            } catch (e: Exception) {
                logger.error(
                    "❌ Unexpected error processing PaymentOrderRetryCommandExecutor orderId=${order.paymentOrderId}, retrying...: ${e.message}",
                    e
                )
                paymentService.processPspResult(
                    event = paymentOrderCreatedEvent,
                    pspStatus = PaymentOrderStatus.UNKNOWN
                )
            }
        }
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            executor.submit<PaymentOrderStatus> {
                try {
                    pspClient.chargeRetry(order)
                } finally {
                    // Only record metric here, don't re-register the DistributionSummary each time!
                    retryMetrics.recordRetryAttempt(order.retryCount, order.retryReason)
                }
            }.get(3, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
        }
    }
}

@Component
class RetryMetrics(meterRegistry: MeterRegistry) {
    // If you want to tag by reason, you can keep a map or register with a static set of reasons
    private val retrySummary = DistributionSummary.builder("paymentorder.retry.attempts")
        .baseUnit("attempts")
        .description("Number of retry attempts before PaymentOrder succeeded")
        .register(meterRegistry)

    fun recordRetryAttempt(retryCount: Int, reason: String?) {
        // You can add .tag("reason", reason ?: "unknown") if you really want, but beware of unbounded label cardinality!
        retrySummary.record(retryCount.toDouble())
    }
}