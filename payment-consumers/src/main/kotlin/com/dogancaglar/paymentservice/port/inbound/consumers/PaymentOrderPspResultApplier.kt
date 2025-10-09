package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentOrderPspResultApplier(
    @param:Qualifier("syncPaymentTx") private val kafkaTx: KafkaTxExecutor,
    private val processPspResult: ProcessPspResultUseCase, // keep your existing service
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_ORDER_PSP_RESULT_UPDATED],
        containerFactory = "${Topics.PAYMENT_ORDER_PSP_RESULT_UPDATED}-factory",
        groupId = CONSUMER_GROUPS.PAYMENT_ORDER_PSP_RESULT_UPDATED
    )
    fun onPspResultUpdated(
        record: ConsumerRecord<String, EventEnvelope<PaymentOrderPspResultUpdated>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val env = record.value()
        val result = env.data

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()
        LogContext.with(env) {
            val mapped = PaymentOrderStatus.valueOf(result.pspStatus)

            kafkaTx.run(offsets, groupMeta) {
                // Reuse your existing service → handles SUCCESS/RETRY/STATUS_CHECK/FINAL
                processPspResult.processPspResult(event = result, pspStatus = mapped)
            }
            logger.info("✅ Applied PSP result (status={}) agg={}", mapped, env.aggregateId)
        }
    }
}