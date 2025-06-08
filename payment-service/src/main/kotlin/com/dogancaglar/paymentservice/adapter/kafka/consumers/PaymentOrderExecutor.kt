package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.domain.internal.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.psp.PSPClient
import io.micrometer.core.instrument.MeterRegistry
import jakarta.transaction.Transactional
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/*
  @Qualifier("paymentRetryStatusAdapter")
    val paymentRetryStatusAdapter: RetryQueuePort<ScheduledPaymentOrderStatusRequest>,
    @Qualifier("paymentRetryPaymentAdapter") val paymentRetryPaymentAdapter: RetryQueuePort<PaymentOrderRetryRequested>,
    val pspClient: PSPClient,
 */
@Component
class PaymentOrderExecutor(
    private val paymentService: PaymentService,
    val pspClient: PSPClient,
    val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        // This method is called when a  event is received from Kafka and we set MDC of consumed event this is gonna be parent for the rest
        val envelope = record.value()
        val paymentOrderCreatedEvent = envelope.data
        val order = paymentService.mapEventToDomain(paymentOrderCreatedEvent)
        LogContext.with(
            envelope, mapOf(
                LogFields.TOPIC_NAME to record.topic(),
                LogFields.CONSUMER_GROUP to "PAYMENT_ORDER_EXECUTOR",
                LogFields.EVENT_ID to envelope.eventId.toString(),
                LogFields.TRACE_ID to envelope.traceId,
                LogFields.PUBLIC_PAYMENT_ID to envelope.data.publicPaymentId,
                LogFields.PUBLIC_PAYMENT_ORDER_ID to envelope.aggregateId
            )
        ) {

            if (order.status != PaymentOrderStatus.INITIATED) {
                logger.info("⏩ Skipping already processed order with status=${order.status}")
                return@with
            }
            try {
                val response = safePspCall(order)
                logger.info("✅ PSP call returned status=$response for paymentOrderId=${order.paymentOrderId}")
                paymentService.processPspResult(event = paymentOrderCreatedEvent, pspStatus = response)
            } catch (e: TimeoutException) {
                logger.error("⏱️ PSP call timed out for orderId=${order.paymentOrderId}, retrying...", e)
                paymentService.processPspResult(
                    event = paymentOrderCreatedEvent,
                    pspStatus = PaymentOrderStatus.TIMEOUT
                )
            } catch (e: Exception) {
                logger.error(
                    "❌ Unexpected error processing orderId=${order.paymentOrderId}, retrying...: ${e.message}",
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
                val start = System.nanoTime()
                try {
                    return@submit pspClient.charge(order)
                } finally {
                    val end = System.nanoTime()
                    meterRegistry.timer("psp.call.duration", "operation", "charge")
                        .record(end - start, TimeUnit.NANOSECONDS)
                }
            }.get(3, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
        }
    }
}