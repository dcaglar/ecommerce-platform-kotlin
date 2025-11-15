package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.application.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.metadata.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.application.metadata.EventMetadatas
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
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
import java.util.concurrent.TimeUnit

@Component
class PaymentOrderCaptureExecutor(
    private val captureClient: PspCaptureGatewayPort,
    private val meterRegistry: MeterRegistry,
    @param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
    @param:Qualifier("syncPaymentEventPublisher") private val publisher: EventPublisherPort,
    private val paymentOrderRepository: PaymentOrderRepository, //read only
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
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

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()

        LogContext.with(env) {
            val current = paymentOrderRepository
                .findByPaymentOrderId(PaymentOrderId(work.paymentOrderId.toLong()))
                .firstOrNull()
            if (current == null) {
                logger.warn(
                    "Dropping PSP call: no PaymentOrder row found for agg={} attempt={}",
                    env.aggregateId, work.retryCount
                )
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            val attempt = work.retryCount
            if (current.retryCount > attempt) {
                logger.warn(
                    "Dropping PSP call: stale attempt agg={} dbRetryCount={} > eventRetryCount={}",
                    env.aggregateId, current.retryCount, attempt
                )
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }
            // Build domain snapshot (no DB load)
            val orderForPsp: PaymentOrder = paymentOrderDomainEventMapper.fromEvent(work)

            // Call PSP (adapter handles timeouts/interrupts)
            var mappedStatus: PaymentOrderStatus? = null
            var errorCode: String? = null
            var errorDetail: String? = null
            val startMs = System.currentTimeMillis()
            mappedStatus = captureClient.capture(orderForPsp)
            val tookMs = System.currentTimeMillis() - startMs
            pspLatency.record(tookMs, TimeUnit.MILLISECONDS)
            // Build result-updated event (extends PaymentOrderEvent)
            val result = PaymentOrderPspResultUpdated.create(
                paymentOrderId = work.paymentOrderId,
                paymentId = work.paymentId,
                sellerId = work.sellerId,
                amountValue = work.amountValue,
                currency = work.currency,
                status = work.status,               // last known status
                createdAt = work.createdAt,
                updatedAt = work.updatedAt,
                retryCount = work.retryCount,       // attempt index (0..n)
                pspStatus = mappedStatus.name,      // will be interpreted by the Applier
                pspErrorCode = errorCode,
                pspErrorDetail = errorDetail,
                latencyMs = tookMs
            )

            val outEnv = DomainEventEnvelopeFactory.envelopeFor(
                data = result,
                eventMetaData = EventMetadatas
                    .PaymentOrderPspResultUpdatedMetadata,
                aggregateId = work.paymentOrderId,          // key = paymentOrderId
                traceId = env.traceId,
                parentEventId = env.eventId
            )

            // Publish PSP_RESULT_UPDATED under Kafka tx, then commit offsets
            kafkaTx.run(offsets, groupMeta) {
                publisher.publishSync(
                    preSetEventIdFromCaller = outEnv.eventId,
                    aggregateId = outEnv.aggregateId,
                    eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
                    data = result,
                    traceId = outEnv.traceId,
                    parentEventId = outEnv.parentEventId
                )
                logger.info(
                    "📤 Published PSP_RESULT_UPDATED attempt={} agg={} traceId={}",
                    result.retryCount, outEnv.aggregateId, outEnv.traceId
                )
            }
        }
    }
}