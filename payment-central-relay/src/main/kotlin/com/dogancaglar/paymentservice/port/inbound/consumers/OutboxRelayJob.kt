package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.ports.outbound.EdgeWatermarkPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxRelayPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.OutboxEventType
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxRelayJob(
    private val centralOutboxRepository: CentralOutboxRelayPort,
    private val watermarkPort: EdgeWatermarkPort,
    @Qualifier("batchPaymentEventPublisher") private val kafkaPublisher: EventPublisherPort,
    @Qualifier("resilientExecutor") private val executor: ThreadPoolTaskExecutor,
    private val objectMapper: ObjectMapper,
    private val serializationPort: SerializationPort,
    @Value("\${outbox-relay.batch-size:500}") private val batchSize: Int,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${outbox-relay.poll-interval:5000}")
    fun poll() {
        val tSafe = watermarkPort.computeTSafe()
        if (tSafe == null) {
            logger.debug("No edge watermarks available, skipping poll")
            return
        }

        val batch = centralOutboxRepository.findEligible(tSafe, batchSize)
        if (batch.isEmpty()) {
            return
        }
        
        logger.debug("Fetched {} eligible events (T_safe = {})", batch.size, tSafe)

        // Group events by aggregateId (e.g. sellerId) to preserve ordering per account
        val groups = batch.groupBy { it.aggregateId }

        // Process each group concurrently on a thread pool, but keep events inside each group strictly sequential
        for ((_, events) in groups) {
            executor.execute {
                for (entry in events) {
                    processEntry(entry)
                }
            }
        }
    }

    private fun processEntry(entry: OutboxEvent) {
        try {
            val ok = when (OutboxEventType.from(entry.eventType)) {
                OutboxEventType.payment_authorized -> {
                    val envelope = convertToPaymentAuthorizedEnvelope(entry)
                    kafkaPublisher.publishBatchAtomically(listOf(envelope), java.time.Duration.ofSeconds(10))
                }

                OutboxEventType.payment_order_capture_received -> {
                    val envelope = convertToPaymentOrderCaptureReceivedEnvelope(entry)
                    kafkaPublisher.publishBatchAtomically(listOf(envelope), java.time.Duration.ofSeconds(10))
                }

                OutboxEventType.payment_order_refund_received -> {
                    val envelope = convertToPaymentOrderRefundReceivedEnvelope(entry)
                    kafkaPublisher.publishBatchAtomically(listOf(envelope), java.time.Duration.ofSeconds(10))
                }

                else -> {
                    logger.warn("❓ Unknown outbox event type=${entry.eventType}, skipping oeid=${entry.oeid}")
                    return
                }
            }

            if (ok) {
                centralOutboxRepository.markDispatched(entry.oeid)
            } else {
                logger.error("Failed to publish event oeid=${entry.oeid} for aggregate ${entry.aggregateId}")
            }
        } catch (e: Exception) {
            logger.error(
                "Error processing event oeid=${entry.oeid} for aggregate ${entry.aggregateId}",
                e
            )
        }
    }
     private fun convertToPaymentAuthorizedEnvelope(evt: OutboxEvent):EventEnvelope<PaymentAuthorized> {

        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, PaymentAuthorized::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<PaymentAuthorized>

        return envelope
    }

     private fun convertToPaymentOrderCaptureReceivedEnvelope(evt: OutboxEvent): EventEnvelope<PaymentOrderCaptureReceived> {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, PaymentOrderCaptureReceived::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<PaymentOrderCaptureReceived>
        return envelope
    }

     private fun convertToPaymentOrderRefundReceivedEnvelope(evt: OutboxEvent): EventEnvelope<com.dogancaglar.paymentservice.application.events.PaymentOrderRefundReceived> {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, com.dogancaglar.paymentservice.application.events.PaymentOrderRefundReceived::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<com.dogancaglar.paymentservice.application.events.PaymentOrderRefundReceived>
        return envelope
    }
}
