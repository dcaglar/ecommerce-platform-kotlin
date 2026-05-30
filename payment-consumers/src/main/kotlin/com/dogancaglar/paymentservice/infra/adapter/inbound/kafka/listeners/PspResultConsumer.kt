package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.listeners

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ID
import com.dogancaglar.common.logging.GenericLogFields.PAYMENT_ORDER_ID
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptured
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefunded
import com.dogancaglar.common.event.Event
import com.dogancaglar.paymentservice.application.service.PspResultProcessingService
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.metadata.Topics
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class PspResultConsumer(
    private val pspResultProcessingService: PspResultProcessingService,
    private val dedupe: EventDeduplicationPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PAYMENT_AUTHORIZED, Topics.PAYMENT_ORDER_CAPTURED_TOPIC, Topics.PAYMENT_ORDER_REFUNDED_TOPIC],
        containerFactory = "psp-result-factory",
        groupId = CONSUMER_GROUPS.PSP_RESULT_CONSUMER
    )
    fun onPspResult(
        record: ConsumerRecord<String, EventEnvelope<Event>>,
        consumer: org.apache.kafka.clients.consumer.Consumer<*, *>
    ) {
        val envelope = record.value()
        val eventData = envelope.data
        val aggregateId = when (eventData) {
            is PaymentOrderCaptured -> eventData.publicPaymentOrderId
            is PaymentOrderRefunded -> eventData.publicPaymentOrderId
            is PaymentAuthorized -> eventData.publicPaymentId
            else -> envelope.aggregateId
        }

        EventLogContext.with(envelope) {
            if (dedupe.exists(envelope.eventId)) {
                logger.warn(
                    "⚠️ Event is processed already for aggregateId $aggregateId, skipping"
                )
                return@with
            }

            logger.info(
                "🎬 Started processing for aggregateId $aggregateId"
            )

            try {
                when (eventData) {
                    is PaymentAuthorized -> pspResultProcessingService.processAuthorized(eventData)
                    is PaymentOrderCaptured -> pspResultProcessingService.processCaptured(eventData)
                    is PaymentOrderRefunded -> pspResultProcessingService.processRefunded(eventData)
                    else -> {
                        logger.warn("⚠️ Received unknown event type: ${eventData.javaClass.simpleName}")
                        return@with
                    }
                }

                dedupe.markProcessed(envelope.eventId, 3600)
                logger.info(
                    "✅ Completed processing PSP result for aggregateId $aggregateId"
                )
            } catch (ex: Exception) {
                logger.error(
                    "🚨 Unexpected error processing PSP result for aggregateId $aggregateId error was : ${ex.message}", ex
                )
                throw ex
            }
        }
    }
}
