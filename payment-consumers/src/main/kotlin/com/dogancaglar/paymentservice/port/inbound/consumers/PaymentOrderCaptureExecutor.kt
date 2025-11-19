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
    private val captureClient: PspCaptureGatewayPort,
    private val meterRegistry: MeterRegistry,
    @param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
    @param:Qualifier("syncPaymentEventPublisher") private val publisher: EventPublisherPort,
    private val paymentOrderRepository: PaymentOrderRepository, //read only
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
    private  val clock: Clock
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

        EventLogContext.with(env) {
            val current = paymentOrderRepository
                .findByPaymentOrderId(PaymentOrderId(work.paymentOrderId.toLong()))
                .firstOrNull()
            if (current == null) {
                logger.warn(
                    "Dropping PSP call: no PaymentOrder row found for agg={} attempt={}",
                    env.aggregateId, work.attempt
                )
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            val attempt = work.attempt
            if (current.retryCount > attempt) {
                logger.warn(
                    "Dropping PSP call: stale attempt agg={} dbRetryCount={} > eventRetryCount={}",
                    env.aggregateId, current.retryCount, attempt
                )
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }
            // Build domain snapshot (no DB load)

            // Call PSP (adapter handles timeouts/interrupts)
            var mappedStatus: PaymentOrderStatus? = null
            var errorCode: String? = null
            var errorDetail: String? = null
            val startMs = System.currentTimeMillis()
            mappedStatus = captureClient.capture(current)
            val tookMs = System.currentTimeMillis() - startMs
            pspLatency.record(tookMs, TimeUnit.MILLISECONDS)
            // Build result-updated event (extends PaymentOrderEvent)
           val result= PaymentOrderPspResultUpdated.from(work,mappedStatus.name,tookMs, LocalDateTime.now(clock))
            val outEnv = EventEnvelopeFactory.envelopeFor(
                data = result,
                aggregateId = work.paymentOrderId,          // key = paymentOrderId
                traceId = env.traceId,
                parentEventId = env.eventId
            )

            // Publish PSP_RESULT_UPDATED under Kafka tx, then commit offsets
            kafkaTx.run(offsets, groupMeta) {
                publisher.publishSync(
                    aggregateId = outEnv.aggregateId,
                    data = result,
                    traceId = outEnv.traceId,
                    parentEventId = outEnv.parentEventId
                )
                logger.info(
                    "📤 Published PSP_RESULT_UPDATED agg={} traceId={},psp status {}",
                  outEnv.aggregateId, outEnv.traceId,outEnv,result.pspStatus
                )
            }
        }
    }
}