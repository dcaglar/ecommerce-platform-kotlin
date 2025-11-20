package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.PaymentEventMetadataCatalog
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PspCaptureGatewayPort
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
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
@Component
class PaymentOrderCaptureExecutor(
    private val psp: PspCaptureGatewayPort,
    private val meterRegistry: MeterRegistry,
    @Qualifier("syncPaymentTx")
    private val kafkaTx: KafkaTxExecutor,
    @Qualifier("syncPaymentEventPublisher")
    private val publisher: EventPublisherPort,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val mapper: PaymentOrderDomainEventMapper,
    private val dedupe: EventDeduplicationPort,
    private val clock: Clock
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val pspLatency: Timer = Timer.builder("psp_call_latency")
        .publishPercentileHistogram()
        .register(meterRegistry)

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE],
        containerFactory = "${Topics.PAYMENT_ORDER_CAPTURE_REQUEST_QUEUE}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_ORDER_CAPTURE_EXECUTOR
    )
    fun onPspRequested(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderCaptureCommand>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val env = record.value()
        val work = env.data
        val eventId = env.eventId

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()

        EventLogContext.with(env) {

            // ---------------------------------------
            // 1. DEDUPE CHECK
            // ---------------------------------------
            if (dedupe.exists(eventId)) {
                logger.debug("🔁 Redis dedupe skip PSP executor eventId={}", eventId)
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            // ---------------------------------------
            // 2. LOAD ORDER + IDENTITY CHECK
            // ---------------------------------------
            val current = paymentOrderRepository
                .findByPaymentOrderId(PaymentOrderId(work.paymentOrderId.toLong()))
                .firstOrNull()

            if (current == null) {
                logger.warn("⏭️ Missing PaymentOrder row for PSP call poId={} eventId={}",
                    work.paymentOrderId, eventId
                )
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            // stale attempt check
            if (current.retryCount > work.attempt) {
                logger.warn(
                    "⏭️ Stale PSP attempt dbRetryCount={} > eventRetryCount={} poId={}",
                    current.retryCount, work.attempt, work.paymentOrderId
                )
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            // status check (must still be CAPTURE_REQUESTED or PENDING_CAPTURE before psp call)
            if (current.status != PaymentOrderStatus.CAPTURE_REQUESTED && current.status!= PaymentOrderStatus.PENDING_CAPTURE) {
                logger.warn(
                    "⏭️ Skip PSP: dbStatus={} expected=CAPTURE_REQUESTED or CAPTURE_PENDING poId={}",
                    current.status, current.paymentOrderId
                )
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
                    work,
                    pspStatus,
                    tookMs,
                    LocalDateTime.now(clock)
                )
            } catch (ex: IllegalArgumentException) {
                // domain invariant failed (invalid PSP status or order state)
                logger.warn(
                    "⏭️ Skipping PSP_RESULT_UPDATED creation: poId={} status={} reason={}",
                    current.paymentOrderId, current.status, ex.message
                )
                kafkaTx.run(offsets, groupMeta) {} // commit offset and skip
                return@with
            } catch (ex: Exception) {
                // unexpected serialization or mapping issue
                logger.error(
                    "❌ Unexpected error building PSP_RESULT_UPDATED for poId={} eventId={}",
                    current.paymentOrderId, eventId, ex
                )
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            val outEnv = EventEnvelopeFactory.envelopeFor(
                data = result,
                aggregateId = work.paymentOrderId,
                traceId = env.traceId,
                parentEventId = env.eventId
            )

            // ---------------------------------------
            // 5. TX: publish + commit + mark dedupe
            // ---------------------------------------
            kafkaTx.run(offsets, groupMeta) {

                publisher.publishSync(
                    aggregateId = outEnv.aggregateId,
                    data = result,
                    traceId = outEnv.traceId,
                    parentEventId = outEnv.parentEventId
                )

                dedupe.markProcessed(eventId, 3600)

                logger.info(
                    "📤 PSP_RESULT_UPDATED published poId={} traceId={} status={}",
                    work.paymentOrderId, outEnv.traceId, result.pspStatus
                )
            }
        }
    }
}