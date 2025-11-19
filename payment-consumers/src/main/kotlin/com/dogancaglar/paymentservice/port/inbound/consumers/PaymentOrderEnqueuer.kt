package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.PaymentEventMetadataCatalog
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.consumers.EventDedupCache
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component


@Component
class PaymentOrderEnqueuer(
    @param:Qualifier("syncPaymentTx")
    private val kafkaTx: KafkaTxExecutor,

    @param:Qualifier("syncPaymentEventPublisher")
    private val publisher: EventPublisherPort,

    private val dedupCache: EventDedupCache,
    private val mapper: PaymentOrderDomainEventMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_CREATED],
        containerFactory = "${Topics.PAYMENT_ORDER_CREATED}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_ORDER_ENQUEUER
    )
    fun onCreated(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val env = record.value()
        val evt = env.data

        // --- OFFSETS ---
        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()

        // --- MDC ---
        EventLogContext.with(env) {

            if (dedupCache.isDuplicate(evt.deterministicEventId())) {
                logger.debug("🔁 Duplicate event detected, skipping eventId={}", evt.deterministicEventId())
                kafkaTx.run(offsets, groupMeta) {}   // still commit offset
                return@with
            }

            // --- Snapshot instead of aggregate ---
            val snapshot = mapper.snapshotFrom(evt)

            // --- Build PSP-call request command ---
            val work = mapper.toPaymentOrderCaptureCommand(snapshot, attempt = 0)

            val outEnv = EventEnvelopeFactory.envelopeFor(
                data = work,
                aggregateId = work.paymentOrderId,
                traceId = env.traceId,
                parentEventId = env.eventId
            )

            // --- Transacted produce+commit ---
            kafkaTx.run(offsets, groupMeta) {
                publisher.publishSync(
                    aggregateId = outEnv.aggregateId,
                    data = work,
                    traceId = outEnv.traceId,
                    parentEventId = outEnv.parentEventId
                )
                logger.info(
                    "📤 Enqueued CAPTURE_REQUESTED attempt=0 paymentOrderId={} traceId={}",
                    work.paymentOrderId, outEnv.traceId
                )
            }
        }
    }
}