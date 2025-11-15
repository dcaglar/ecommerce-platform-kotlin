package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.application.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.metadata.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class PaymentAuthorizedConsumer(
    @param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
    @param:Qualifier("syncPaymentEventPublisher") private val publisher: EventPublisherPort,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_AUTHORIZED],
        containerFactory = "${Topics.PAYMENT_AUTHORIZED}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_AUTHORIZED_CONSUMER
    )
    fun onCreated(
        record: ConsumerRecord<String, EventEnvelope<PaymentAuthorized>>,
        consumer: Consumer<*, *>
    ) {
        val consumed = record.value()

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta =
            consumer.groupMetadata()                        // <— real metadata (generation, member id, epoch)
        LogContext.with(consumed) {
            /*
            if (order.status != PaymentStatus.AUTHORIZED) {
                kafkaTx.run(offsets, groupMeta) {}
                logger.warn("⏩ Skip authorized consumer (status={}) agg={}", order.status, consumed.aggregateId)
                return@with
            }
            */

            //todo we will request a ledger recording here
            /*


            kafkaTx.run(offsets, groupMeta) {
                publisher.publishSync(
                    preSetEventIdFromCaller = outEnv.eventId,
                    aggregateId = outEnv.aggregateId,
                    eventMetaData = EventMetadatas.PaymentOrderCaptureCommandMetadata,
                    data = work,
                    traceId = outEnv.traceId,
                    parentEventId = outEnv.parentEventId
                )
                logger.debug("📤 Enqueued PSP work attempt=0 agg={} traceId={}", outEnv.aggregateId, outEnv.traceId)
            }
            */

        }

    }
}