package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ORDER_ID
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.config.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PspModificationGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
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
    @param:Qualifier("syncPaymentTx")
    private val kafkaTx: KafkaTxExecutor,
    @param:Qualifier("syncPaymentEventPublisher")
    private val publisher: EventPublisherPort,
    private val paymentOrderRepository: PaymentOrderRepository,
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

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()

        EventLogContext.with(envelope) {
            // 1. DEDUPE CHECK
            // ---------------------------------------
            if (dedupe.exists(eventId)) {
                logger.warn(
                    "⚠️ Event is processed already  for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId} , skipping")
                logger.debug("🔁 Redis dedupe skip PSP executor eventId={}", eventId)
                kafkaTx.run(offsets, groupMeta) {}
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
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            // stale attempt check
            if (current.retryCount > eventData.attempt) {
                logger.warn(
                    "⚠️  Stale PSP attempt dbRetryCount ${current.retryCount} eventRetryCount ${eventData.attempt}  for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId}")
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            // (must still be CAPTURE_REQUESTED or PENDING_CAPTURE before psp call)
            if (current.status != PaymentOrderStatus.CAPTURE_REQUESTED && current.status!= PaymentOrderStatus.PENDING_CAPTURE) {
                logger.warn(
                    "⚠️ Skip PSP: dbStatus=${current.status.name} expected=CAPTURE_REQUESTED or CAPTURE_PENDING   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId}")
                kafkaTx.run(offsets, groupMeta) {}
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
            // 4. BUILD UPDATED EVENT
            // ---------------------------------------
            val result = try {
                PaymentOrderPspResultUpdated.from(
                    eventData,
                    pspStatus,
                    tookMs,
                    Utc.nowInstant()
                )
            } catch (ex: IllegalArgumentException) {
                // domain invariant failed (invalid PSP status or order state)
                logger.warn(
                    "‼️  Invalid PSP_RESULT_UPDATED creation   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId}")
                kafkaTx.run(offsets, groupMeta) {} // commit offset and skip
                return@with
            } catch (ex: Exception) {
                // unexpected serialization or mapping issue
                logger.error(
                    "🚨 Unexpected error building PSP_RESULT_UPDATED for for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId} error was : ${ex.message}")
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }


            // ---------------------------------------
            // 5. TX: publish + commit + mark dedupe
            // ---------------------------------------
            kafkaTx.run(offsets, groupMeta) {

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
}