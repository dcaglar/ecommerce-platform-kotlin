package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PaymentOrderPspResultApplier(
    @Qualifier("syncPaymentTx")
    private val kafkaTx: KafkaTxExecutor,
    private val processPspResult: ProcessPspResultUseCase,
    private val dedupe: EventDeduplicationPort,
    private val paymentOrderModificationPort: PaymentOrderModificationPort
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
        val evt = env.data
        val eventId = env.eventId

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()

        EventLogContext.with(env) {

            if (dedupe.exists(eventId)) {
                logger.debug("🔁 Skip duplicate PSP_RESULT_UPDATED eventId={}", eventId)
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }

            val status = runCatching { PaymentOrderStatus.valueOf(evt.pspStatus) }
                .getOrElse {
                    logger.warn("❌ Invalid PSP status={} for poId={}, skipping", evt.pspStatus, env.aggregateId)
                    kafkaTx.run(offsets, groupMeta) {}
                    return@with
                }

            // Capture variables for use in lambda
            val capturedStatus = status
            val capturedEventId = eventId
            
            kafkaTx.run(offsets, groupMeta) {
                val order: PaymentOrder? = paymentOrderModificationPort.findByPaymentOrderId(PaymentOrderId(evt.paymentOrderId.toLong()))
                if (order == null) {
                    logger.warn("⚠️ Missing PaymentOrder row for {}", evt.paymentOrderId)
                    return@run
                }
                if (order.isTerminal()) {
                    logger.info("ℹ️ Skipping PSP result for terminal order. poId={} currentStatus={} pspStatus={} eventId={}", 
                        order.paymentOrderId.value, order.status, capturedStatus, capturedEventId)
                    dedupe.markProcessed(capturedEventId, 3600)
                    return@run  // Graceful return - no exception, no DLQ, but still commit offset and mark dedup
                }
                processPspResult.processPspResult(evt, order)
                dedupe.markProcessed(capturedEventId, 3600)
                logger.info("✅ PSP result applied status={} agg={} eventId={}", capturedStatus, env.aggregateId, capturedEventId)
            }
        }
    }
}