package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.service.ProcessPspResultProcessingService
import com.dogancaglar.common.kafka.metadata.CONSUMER_GROUPS
import com.dogancaglar.common.kafka.metadata.Topics
import com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import com.dogancaglar.common.event.Event
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.CaptureConfirmed
import com.dogancaglar.paymentservice.application.events.InternalTransferCommand
import com.dogancaglar.paymentservice.ports.inbound.usecases.ProcessPspResultUseCase
import org.apache.kafka.clients.consumer.Consumer

/**
 * PspResultConsumer
 * 
 * Mandate: Listens to psp-result-queue and delegates to ProcessPspResultProcessingService
 * to execute real financial state mutations in a transactional manner.
 */
@Component
class PspResultConsumer(
    private val processPspResultUseCase: ProcessPspResultUseCase,
    private val dedupe: EventDeduplicationPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [Topics.PSP_RESULTS],
        containerFactory = CONSUMER_GROUPS.PSP_RESULT_CONSUMER + "-factory",
        groupId = CONSUMER_GROUPS.PSP_RESULT_CONSUMER
    )
    fun onPspResult(
        record: ConsumerRecord<String, EventEnvelope<Event>>,
        consumer: Consumer<*, *>
    ) {
        val envelope = record.value()
        EventLogContext.with(envelope) {
            val exists = dedupe.exists(record.value().eventId)
            if (exists) {
                logger.warn("⚠️ Event is processed already, skipping eventId=${record.value().eventId}")
                return@with
            }

            val event = record.value().data

            try {
                //default if it ist auth+capture
                when (event) {
                    is PaymentAuthorized -> {
                        logger.info("🎬 Processing PaymentAuthorized event for paymentIntentId: ${event.paymentIntentId}")
                        processPspResultUseCase.processAuthorized(event)

                    }

                    is CaptureConfirmed -> {
                        logger.info("🎬 Processing CaptureConfirmed event for paymentIntentId: ${event.publicPaymentIntentId}")
                        processPspResultUseCase.processCaptureConfirmed(event)
                    }

                    is InternalTransferCommand -> {
                        logger.info("🎬 Processing InternalTransferCommand event for target: ${event.targetEntityId}")
                        processPspResultUseCase.processInternalTransferCommand(event)
                    }

                    else -> {
                        logger.warn("⚠️ Unhandled event type in PspResultConsumer: ${event.javaClass.name}")
                    }
                }

                dedupe.markProcessed(record.value().eventId, 3600)
            } catch (e: Exception) {
                logger.error(
                    "❌ Failed to process event ${event.javaClass.simpleName} with eventId: ${record.value().eventId}",
                    e
                )
                throw e // Let Kafka handle retry/DLQ
            }
        }
    }
}
