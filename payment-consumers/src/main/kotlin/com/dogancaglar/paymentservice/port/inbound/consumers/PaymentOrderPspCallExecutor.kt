package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class PaymentOrderPspCallExecutor(
    private val pspClient: PaymentGatewayPort,
    private val meterRegistry: MeterRegistry,
    private val kafkaTx: KafkaTxExecutor,
    private val publisher: EventPublisherPort,
    private val paymentOrderRepository: PaymentOrderRepository //read only
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val pspLatency: Timer = Timer.builder("psp_call_latency")
        .publishPercentileHistogram()
        .register(meterRegistry)

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_PSP_CALL_REQUESTED],
        containerFactory = "${Topics.PAYMENT_ORDER_PSP_CALL_REQUESTED}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_ORDER_PSP_CALL_EXECUTOR
    )
    fun onPspRequested(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderPspCallRequested>>,
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

            if (current.isTerminal()) {
                logger.warn(
                    "Dropping PSP call: terminal status={} agg={} attempt={}",
                    current.status, env.aggregateId, work.retryCount
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
            val orderForPsp: PaymentOrder = PaymentOrderDomainEventMapper.fromEvent(work)

            // Call PSP (adapter handles timeouts/interrupts)
            var mappedStatus: PaymentOrderStatus? = null
            var errorCode: String? = null
            var errorDetail: String? = null
            val startMs = System.currentTimeMillis()
            mappedStatus = pspClient.charge(orderForPsp)
            val tookMs = System.currentTimeMillis() - startMs
            pspLatency.record(tookMs, TimeUnit.MILLISECONDS)
            // Build result-updated event (extends PaymentOrderEvent)
            val result = PaymentOrderPspResultUpdated(
                paymentOrderId = work.paymentOrderId,
                publicPaymentOrderId = work.publicPaymentOrderId,
                paymentId = work.paymentId,
                publicPaymentId = work.publicPaymentId,
                sellerId = work.sellerId,
                amountValue = work.amountValue,
                currency = work.currency,
                status = work.status,               // last known status
                createdAt = work.createdAt,
                updatedAt = work.updatedAt,
                retryCount = work.retryCount,       // attempt index (0..n)
                retryReason = work.retryReason,
                lastErrorMessage = work.lastErrorMessage,
                pspStatus = mappedStatus.name,      // will be interpreted by the Applier
                pspErrorCode = errorCode,
                pspErrorDetail = errorDetail,
                latencyMs = tookMs
            )

            val outEnv = DomainEventEnvelopeFactory.envelopeFor(
                data = result,
                eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
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