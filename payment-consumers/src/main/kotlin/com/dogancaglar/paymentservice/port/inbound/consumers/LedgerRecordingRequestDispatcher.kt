package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent
import com.dogancaglar.paymentservice.ports.inbound.RequestLedgerRecordingUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
@Component
class LedgerRecordingRequestDispatcher(
    @param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
    @param:Qualifier("syncPaymentEventPublisher") private val publisher: EventPublisherPort,
    private val requestLedgerRecordingUseCase: RequestLedgerRecordingUseCase
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_FINALIZED],
        containerFactory = "${Topics.PAYMENT_ORDER_FINALIZED}-factory",
        groupId = CONSUMER_GROUPS.LEDGER_RECORDING_REQUEST_DISPATCHER
    )
    fun onPaymentOrderFinalized(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderEvent>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val env = record.value()
        val event = env.data

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()

        LogContext.with(env) {
            kafkaTx.run(offsets, groupMeta) {
                logger.info(
                    "🟢 Received finalized PaymentOrder (status={}) → dispatching LedgerRecordingCommand for agg={} traceId={}",
                    event.status, env.aggregateId, env.traceId
                )
                requestLedgerRecordingUseCase.requestLedgerRecording(event)
            }
        }
    }
}