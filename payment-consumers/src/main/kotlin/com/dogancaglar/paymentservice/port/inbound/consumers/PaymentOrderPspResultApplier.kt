package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ORDER_ID
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.config.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.inbound.usecases.ProcessPspResultUseCase
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
    @param:Qualifier("syncPaymentTx")
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
        val envelope = record.value()
        val eventData = envelope.data
        val eventId = envelope.eventId

        val tp = TopicPartition(record.topic(), record.partition())
        val offsets = mapOf(tp to OffsetAndMetadata(record.offset() + 1))
        val groupMeta = consumer.groupMetadata()

        EventLogContext.with(envelope) {
            if (dedupe.exists(eventId)) {
                logger.warn(
                    "⚠️ Event is processed already  for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId} , skipping")
                kafkaTx.run(offsets, groupMeta) {}
                return@with
            }
            logger.info(
                "🎬 Started processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                        "with $PAYMENT_ID ${eventData.publicPaymentId}")


            val status = runCatching { PaymentOrderStatus.valueOf(eventData.pspStatus) }
                .getOrElse {
                    logger.warn("❌ Invalid PSP status={} for poId={}, skipping", eventData.pspStatus, envelope.aggregateId)
                    kafkaTx.run(offsets, groupMeta) {}
                    return@with
                }

            // Capture variables for use in lambda
            val capturedStatus = status

            kafkaTx.run(offsets, groupMeta) {
                val order: PaymentOrder? = paymentOrderModificationPort.findByPaymentOrderId(PaymentOrderId(eventData.paymentOrderId.toLong()))
                if (order == null) {
                    logger.warn("⚠️ Missing PaymentOrder row for {}", eventData.paymentOrderId)
                    return@run
                }
                if (order.isTerminal()) {
                    logger.info("ℹ️ Skipping PSP result for terminal order. poId={} currentStatus={} pspStatus={} eventId={}", 
                        order.paymentOrderId.value, order.status, capturedStatus, eventId)
                    dedupe.markProcessed(eventId, 3600)
                    return@run  // Graceful return - no exception, no DLQ, but still commit offset and mark dedup
                }
                processPspResult.processPspResult(eventData, order)
                dedupe.markProcessed(eventId, 3600)
                logger.info(
                    "✅ Completed processing   for $PAYMENT_ORDER_ID  ${eventData.publicPaymentOrderId} " +
                            "with $PAYMENT_ID ${eventData.publicPaymentId}")
            }
        }
    }
}