package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import com.dogancaglar.paymentservice.service.MissingPaymentOrderException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
@Component
class PaymentOrderEnqueuer(
    @Qualifier("syncPaymentTx")
    private val kafkaTx: KafkaTxExecutor,

    @Qualifier("syncPaymentEventPublisher")
    private val publisher: EventPublisherPort,

    private val dedupe: EventDeduplicationPort,
    private val modification: PaymentOrderModificationPort,
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
        val eventId = env.eventId

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()

        EventLogContext.with(env) {

            // 1. DEDUPE CHECK
            if (dedupe.exists(eventId)) {
                logger.debug("🔁 Redis dedupe skip eventId={}", eventId)
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            // 2. UPDATE DB → mark as CAPTURE_REQUESTED
            val updated = try {
                modification.markAsCaptureRequested(evt.paymentOrderId.toLong())
            } catch (ex: MissingPaymentOrderException) {
                logger.warn("⏭️ Stale/Missing order during enqueue poId={} eventId={}", evt.paymentOrderId, eventId)
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            // 3. Build new event
            val work = mapper.toPaymentOrderCaptureCommand(updated, attempt = 0)

            val outEnv = EventEnvelopeFactory.envelopeFor(
                data = work,
                aggregateId = work.paymentOrderId,
                traceId = env.traceId,
                parentEventId = env.eventId
            )

            // 4. KAFKA TX: publish + commit offset + mark dedupe
            kafkaTx.run(offsets, groupMeta) {

                publisher.publishSync(
                    aggregateId = outEnv.aggregateId,
                    data = work,
                    traceId = outEnv.traceId,
                    parentEventId = outEnv.parentEventId
                )

                // 5. Mark as processed ONLY HERE — AFTER EVERYTHING SUCCEEDS
                dedupe.markProcessed(eventId, 3600)

                logger.info(
                    "📤 Enqueued CAPTURE_REQUESTED attempt=0 paymentOrderId={} traceId={}",
                    work.paymentOrderId,
                    outEnv.traceId
                )
            }
        }
    }
}