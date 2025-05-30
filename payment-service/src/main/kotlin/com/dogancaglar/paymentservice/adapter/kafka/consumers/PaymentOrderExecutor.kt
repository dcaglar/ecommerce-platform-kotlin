package com.dogancaglar.paymentservice.adapter.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.common.logging.LogFields
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.event.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.application.event.ScheduledPaymentOrderStatusRequest
import com.dogancaglar.paymentservice.application.mapper.PaymentOrderEventMapper
import com.dogancaglar.paymentservice.application.service.PaymentService
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.port.PaymentOrderOutboundPort
import com.dogancaglar.paymentservice.domain.port.RetryQueuePort
import com.dogancaglar.paymentservice.psp.PSPClient
import com.dogancaglar.paymentservice.psp.PSPStatusMapper
import io.micrometer.core.instrument.MeterRegistry
import jakarta.transaction.Transactional
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class PaymentOrderExecutor(
    private val paymentService: PaymentService,
    private val paymentOrderRepository: PaymentOrderOutboundPort,
    @Qualifier("paymentRetryStatusAdapter")
    val paymentRetryStatusAdapter: RetryQueuePort<ScheduledPaymentOrderStatusRequest>,
    @Qualifier("paymentRetryPaymentAdapter") val paymentRetryPaymentAdapter: RetryQueuePort<PaymentOrderRetryRequested>,
    val pspClient: PSPClient,
    val paymentEventPublisher: PaymentEventPublisher, val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>) {
        val envelope = record.value()
        val paymentOrderCreatedEvent = envelope.data
        val order = paymentService.mapEventToDomain(paymentOrderCreatedEvent)
        LogContext.with(
            envelope, mapOf(
                LogFields.TOPIC_NAME to record.topic(),
                LogFields.CONSUMER_GROUP to "payment-order-executor",
                LogFields.PUBLIC_PAYMENT_ID to envelope.data.publicPaymentId,
                LogFields.PUBLIC_PAYMENT_ORDER_ID to envelope.data.publicPaymentOrderId,
            )
        ) {
            logger.info("▶️ [Handle Start] Processing PaymentOrderCreated")

            if (order.status != PaymentOrderStatus.INITIATED) {
                logger.info("⏩ Skipping already processed order with status=${order.status}")
                return@with
            }
            try {
                val response = safePspCall(order)
                logger.info("✅ PSP call returned status=$response for paymentOrderId=${order.paymentOrderId}")
                when {
                    response == PaymentOrderStatus.SUCCESSFUL -> {
                        handleSuccess(envelope,order)
                    }
                    //TODO PAYMENT_NOT__SCUCESFUL_EVENT PUBLISH
                    PSPStatusMapper.requiresRetryPayment(response) -> {
                        handleRetryPayment(envelope, order, reason = "Retryable status from PSP: $response")

                    }

                    PSPStatusMapper.requiresStatusCheck(response) -> {
                        handleSchedulePaymentStatusCheck(
                            parentEventEnvelope = envelope,
                            order = order,
                            reason = "Scheduling a   status check from PSP: $response"
                        )
                    }

                    else -> {
                        handleNonRetryable(order, response)
                    }

                }
            } catch (e: TimeoutException) {
                logger.error("⏱️ PSP call timed out for orderId=${order.paymentOrderId}, retrying...", e)
                handleRetryPayment(envelope, order, "TIMEOUT", e.message)
            } catch (e: Exception) {
                logger.error(
                    "❌ Unexpected error processing orderId=${order.paymentOrderId}, retrying...: ${e.message}",
                    e
                )
                handleRetryPayment(envelope, order, e.message, e.message)
            }
        }

    }

    private fun handleSuccess(envelope: EventEnvelope<PaymentOrderCreated>, order: PaymentOrder) {
        meterRegistry.counter("payment.order.succeeded.total").increment()
        val updatedOrder = paymentService.processSuccessfulPayment(order);

        //but publish success event here
        val publishedEvent = paymentEventPublisher.publish(
            event = EventMetadatas.PaymentOrderSuccededMetaData,
            aggregateId = updatedOrder.publicPaymentOrderId,
            data = PaymentOrderEventMapper.toPaymentOrderSuccededEvent(updatedOrder),
            parentEnvelope = envelope,
        )


        logger.info("📨 Emitted follow-up event with ID=${publishedEvent.eventId}")        //todo do not push yet.then weiwill add eventmetatada
    }

    private fun handleSchedulePaymentStatusCheck(
        parentEventEnvelope: EventEnvelope<PaymentOrderCreated>,
        order: PaymentOrder,
        reason: String? = "",
        error: String? = ""
    ) {/*
        //prepre schedul request for future but emit PAyment_Scheduled with parent payment_request
        val failedOrder = paymentService.handleRetryAttempt(order, reason, error)
        if (failedOrder.second) {
            //excceded it return
            logger.warn("Max atttempt excceeeded")
        }
        paymentRetryStatusAdapter.scheduleRetry(failedOrder)
        val paymentOrderStatusScheduled = failedOrder.toPaymentOrderStatusScheduled(reason, error)
        paymentEventPublisher.publish(
            event = EventMetadatas.PaymentOrderStatusCheckScheduledMetadata.eventType,
            aggregateId = failedOrder.first.paymentOrderPublicId,
            data = paymentOrderStatusScheduled,
            parentEnvelope = parentEventEnvelope
        )
        */
    }


    private fun handleRetryPayment2(
        parentEnvelope: EventEnvelope<PaymentOrderCreated>,
        order: PaymentOrder,
        reason: String? = "",
        error: String? = ""
    ) {
        /*
        //did change to domain
        val failedOrderPair = paymentService.handleRetryAttempt(order, reason, error)
        if (failedOrderPair.second) {
            //excceded it return
            logger.warn("Max atttempt excceeeded")
        } else {
            paymentRetryPaymentAdapter.scheduleRetry(failedOrderPair.first)
            val retryPaymentEvent = //generate failed patment event from domain
                paymentEventPublisher.publish(
                    event = EventMetadatas.PaymentOrderRetryRequestedMetadata.eventType,
                    aggregateId = failedOrderPair.first.paymentOrderPublicId,
                    data = retryPaymentEvent,
                    parentEnvelope = parentEnvelope
                )
        }
        meterRegistry.counter("payment.retry.attempts.total").increment()
        meterRegistry.counter("payment.retry.attempts", "reason", reason ?: "unknown").increment()

         */
    }


    private fun handleRetryPayment(
        parentEnvelope: EventEnvelope<PaymentOrderCreated>,
        order: PaymentOrder,
        reason: String? = "",
        error: String? = ""
    ) {/*
        // 1. 💡 Let service mutate domain + return if retry limit is hit
        val failedOrderPair = paymentService.handleRetryAttempt(order, reason, error)

        // 3. 📊 Record observability metrics
        meterRegistry.counter("payment.retry.attempts.total").increment()
        meterRegistry.counter("payment.retry.attempts", "reason", reason ?: "unknown").increment()

        // 4. 🧠 Retry decision
        if (failedOrderPair.second) {
            logger.warn("⚠️ Max attempts exceeded for ${updatedOrder.publicId}, not retrying further.")
            return
        }

        // 5. 🔁 Schedule retry
        paymentRetryPaymentAdapter.scheduleRetry(updatedOrder)

        // 6. 📤 Publish retry event
        val retryPaymentEvent = failedOrderPair.first.toRetryEvent()
        paymentEventPublisher.publish(
            event = EventMetadatas.PaymentOrderRetryRequestedMetadata.eventType,
            aggregateId = failedOrderPair.first.paymentOrderPublicId,
            data = retryPaymentEvent,
            parentEnvelope = parentEnvelope
        )

        logger.warn("🔁 Scheduled retry for ${failedOrderPair.first.paymentOrderPublicId} (retry=${failedOrderPair.first.retryCount}), reason=$reason")
        */
    }

    private fun handleNonRetryable(order: PaymentOrder, status: PaymentOrderStatus) {
        /*
        val finalizedOrder = order.markAsFinalizedFailed()
            .withRetryReason("Non-retryable PSP status: $status").updatedAt(
                LocalDateTime.now()
            )

        paymentOrderRepository.save(finalizedOrder)
        logger.warn("❗ Order=${order.paymentOrderId} marked as permanently failed with PSP status=$status")

         */
    }

    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
        return CompletableFuture.supplyAsync {
            pspClient.charge(order)
        }.get(3, TimeUnit.SECONDS)
        // This should be replaced with your actual PSP integration
    }

    /*
    private fun safePspCall(order: PaymentOrder): PaymentOrderStatus {
    return meterRegistry.timer("psp.call.duration").recordCallable {
        CompletableFuture.supplyAsync {
            pspClient.charge(order)
        }.get(3, TimeUnit.SECONDS)
    }
}}
     */
}