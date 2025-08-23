package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.util.PaymentOrderFactory
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentOrderEnqueuer(
    private val kafkaTx: KafkaTxExecutor,
    private val publisher: EventPublisherPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val factory = PaymentOrderFactory()

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_CREATED],
        containerFactory = "${Topics.PAYMENT_ORDER_CREATED}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_ORDER_ENQUEUER
    )
    fun onCreated(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderCreated>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val consumed = record.value()
        val created = consumed.data
        val order = factory.fromEvent(created)

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta =
            consumer.groupMetadata()                        // <— real metadata (generation, member id, epoch)
        LogContext.with(consumed) {
            if (order.status != PaymentOrderStatus.INITIATED_PENDING) {
                kafkaTx.run(offsets, groupMeta) {}
                logger.info("⏩ Skip enqueue (status={}) agg={}", order.status, consumed.aggregateId)
                return@with
            }

            val work = PaymentOrderDomainEventMapper.toPaymentOrderPspCallRequested(order, attempt = 0)
            val outEnv = DomainEventEnvelopeFactory.envelopeFor(
                data = work,
                eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
                aggregateId = work.paymentOrderId, // Kafka key = paymentOrderId
                traceId = consumed.traceId,
                parentEventId = consumed.eventId
            )

            kafkaTx.run(offsets, groupMeta) {
                publisher.publishSync(
                    preSetEventIdFromCaller = outEnv.eventId,
                    aggregateId = outEnv.aggregateId,
                    eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
                    data = work,
                    traceId = outEnv.traceId,
                    parentEventId = outEnv.parentEventId
                )
                logger.info("📤 Enqueued PSP work attempt=0 agg={} traceId={}", outEnv.aggregateId, outEnv.traceId)
            }
        }
    }
}