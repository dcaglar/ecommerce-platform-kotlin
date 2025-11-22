package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ORDER_ID
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

@Component
class PaymentAuthorizedConsumer(
    @param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
    @param:Qualifier("syncPaymentEventPublisher") private val publisher: EventPublisherPort,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
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
        val envelope = record.value()
        val eventData = envelope.data

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()
        EventLogContext.with(envelope) {
            /*
            if (order.status != PaymentStatus.AUTHORIZED) {
                kafkaTx.run(offsets, groupMeta) {}
                logger.warn("⏩ Skip authorized consumer (status={}) agg={}", order.status, consumed.aggregateId)
                return@with
            }
            */
            logger.info(
                "🎬 Started processing   authorization event for $PAYMENT_ID  ${eventData.publicPaymentId}")


            kafkaTx.run(offsets, groupMeta) {}
            logger.info(
                "✅ Completed processing authorization payment event   for  with $PAYMENT_ID ${eventData.publicPaymentId}")
            return@with
            //todo we will request a ledger recording here

        }

    }
}