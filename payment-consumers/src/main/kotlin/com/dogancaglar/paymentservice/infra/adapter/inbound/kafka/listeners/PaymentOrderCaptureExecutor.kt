package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.listeners

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ORDER_ID
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptured
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PspModificationGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata.Topics
import java.util.concurrent.TimeUnit
@Component
class PaymentOrderCaptureExecutor(
    private val psp: PspModificationGatewayPort,
    private val meterRegistry: MeterRegistry,
    @param:Qualifier("syncPaymentEventPublisher")
    private val publisher: EventPublisherPort,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val paymentOrderModificationPort: PaymentOrderModificationPort,
    private val dedupe: EventDeduplicationPort
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val pspLatency: Timer = Timer.builder("psp_call_latency")
        .publishPercentileHistogram()
        .register(meterRegistry)

    @KafkaListener(
        topics = [Topics.CAPTURE_QUEUE],
        containerFactory = "${Topics.CAPTURE_QUEUE}-factory",
        groupId = CONSUMER_GROUPS.PSP_CAPTURE_EXECUTOR
    )
    fun onPspRequested(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderCaptureCommand>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val envelope = record.value()
        val eventData = envelope.data
        val eventId = envelope.eventId

        EventLogContext.with(envelope) {
            // 1. DEDUPE CHECK
            // ---------------------------------------
            if (dedupe.exists(eventId)) {
                logger.warn(
                    "⚠️ Event is processed already  for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId} , skipping")
                logger.debug("🔁 Redis dedupe skip PSP executor eventId={}", eventId)
                return@with
            }
            logger.info(
                "🎬 Started processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                        "with $PAYMENT_ID ${eventData.publicPaymentId}")
            // ---------------------------------------


            // ---------------------------------------
            // 2. LOAD ORDER + IDENTITY CHECK
            // ---------------------------------------
            val current = paymentOrderRepository
                .findByPaymentOrderId(PaymentOrderId(eventData.paymentOrderId.toLong()))
                .firstOrNull()

            if (current == null) {
                logger.warn(
                    "‼️ Missing PaymentOrder row for PSP call  for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId}")
                return@with
            }

            // stale attempt check
            if (current.retryCount > eventData.attempt) {
                logger.warn(
                    "⚠️  Stale PSP attempt dbRetryCount ${current.retryCount} eventRetryCount ${eventData.attempt}  for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId}")
                return@with
            }

            // (must still be CAPTURE_REQUESTED or PENDING_CAPTURE before psp call)
            if (current.status != PaymentOrderStatus.CAPTURE_REQUESTED && current.status!= PaymentOrderStatus.PENDING_CAPTURE) {
                logger.warn(
                    "⚠️ Skip PSP: dbStatus=${current.status.name} expected=CAPTURE_REQUESTED or CAPTURE_PENDING   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId}")
                return@with
            }

            // ---------------------------------------
            // 3. PSP CALL
            // ---------------------------------------
            val startMs = System.currentTimeMillis()

            val pspStatus = psp.capture(current)

            val tookMs = System.currentTimeMillis() - startMs
            pspLatency.record(tookMs, TimeUnit.MILLISECONDS)

            // ---------------------------------------
            // 4. UPDATE DB AND BUILD EVENT
            // ---------------------------------------

            val updatedOrder = when (pspStatus) {
                PaymentOrderStatus.CAPTURED -> current.markAsCaptured()
                PaymentOrderStatus.CAPTURE_FAILED -> current.markCaptureDeclined()
                PaymentOrderStatus.PENDING_CAPTURE -> current.markCapturePendingAndIncrementRetry()
                else -> current
            }
            paymentOrderModificationPort.updateReturningIdempotent(updatedOrder)

            val result = try {
                if (pspStatus == PaymentOrderStatus.CAPTURED) {
                    PaymentOrderCaptured.from(updatedOrder, Utc.nowInstant())
                } else {
                    PaymentOrderFinalized.from(updatedOrder, Utc.nowInstant(), pspStatus)
                }
            } catch (ex: Exception) {
                logger.error(
                    "🚨 Unexpected error building EVENT for for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId} error was : ${ex.message}")
                return@with
            }


            // ---------------------------------------
            // 5. TX: publish + commit + mark dedupe
            // ---------------------------------------
            publisher.publishSync(
                aggregateId = envelope.aggregateId,
                data = result,
                traceId = EventLogContext.getTraceId(),
                parentEventId = EventLogContext.getEventId()
            )

            dedupe.markProcessed(eventId, 3600)

            logger.info(
                "✅ Completed processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                        "with $PAYMENT_ID ${eventData.publicPaymentId}")
        }
    }
}