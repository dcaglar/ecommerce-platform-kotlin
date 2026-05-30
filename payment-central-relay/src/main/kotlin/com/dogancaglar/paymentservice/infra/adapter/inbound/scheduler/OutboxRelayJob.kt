package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxRelayPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity.OutboxEventType
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.CompletableFuture
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
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
    @Qualifier("batchPaymentEventPublisher") private val kafkaPublisher: EventPublisherPort,
    @Qualifier("resilientExecutor") private val executor: ThreadPoolTaskExecutor,
    private val objectMapper: ObjectMapper,
    private val serializationPort: SerializationPort,
    @Value("\${outbox-relay.batch-size:500}") private val batchSize: Int,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        Gauge.builder("central_outbox_backlog_size", this) {
            val tSafe = centralOutboxRepository.computeTSafe()
            if (tSafe != null) {
                centralOutboxRepository.countEligible(tSafe).toDouble()
            } else {
                0.0
            }
        }.register(meterRegistry)
    }

    @Scheduled(fixedDelayString = "\${outbox-relay.poll-interval:5000}")
    fun poll() {
        val sample = Timer.start(meterRegistry)
        val tSafe = centralOutboxRepository.computeTSafe()
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

        // Process each group concurrently, but keep events inside each group strictly sequential
        for ((_, events) in groups) {
            executor.execute {
                var chain = CompletableFuture.completedFuture<Void>(null)
                for (entry in events) {
                    chain = chain.thenCompose { processEntryAsync(entry) }
                }
            }
        }
        sample.stop(meterRegistry.timer("relay_poll_duration"))
    }

    private fun processEntryAsync(entry: OutboxEvent): CompletableFuture<Void> {
        val future = try {
            when (OutboxEventType.from(entry.eventType)) {
                OutboxEventType.payment_authorized -> {
                    val envelope = convertToPaymentAuthorizedEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                OutboxEventType.payment_order_capture_received -> {
                    val envelope = convertToPaymentOrderCaptureReceivedEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                OutboxEventType.payment_order_refund_received -> {
                    val envelope = convertToPaymentOrderRefundReceivedEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                OutboxEventType.payment_order_captured -> {
                    val envelope = convertToPaymentOrderCapturedEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                OutboxEventType.payment_order_refunded -> {
                    val envelope = convertToPaymentOrderRefundedEnvelope(entry)
                    kafkaPublisher.publishAsync(envelope)
                }
                else -> {
                    logger.warn("❓ Unknown outbox event type=${entry.eventType}, skipping oeid=${entry.oeid}")
                    CompletableFuture.completedFuture(null)
                }
            }
        } catch (e: Exception) {
            logger.error("Error starting processing for event oeid=${entry.oeid}", e)
            val failedFuture = CompletableFuture<Void>()
            failedFuture.completeExceptionally(e)
            failedFuture
        }

        return future.handle { _, exception ->
            if (exception == null) {
                centralOutboxRepository.markDispatched(entry.oeid)
                meterRegistry.counter("relay_published_total").increment()
                null
            } else {
                meterRegistry.counter("relay_publish_failed_total").increment()
                logger.error("Failed to publish event oeid=${entry.oeid} for aggregate ${entry.aggregateId}", exception)
                throw exception
            }
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

     private fun convertToPaymentOrderCapturedEnvelope(evt: OutboxEvent): EventEnvelope<com.dogancaglar.paymentservice.application.events.PaymentOrderCaptured> {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, com.dogancaglar.paymentservice.application.events.PaymentOrderCaptured::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<com.dogancaglar.paymentservice.application.events.PaymentOrderCaptured>
        return envelope
    }

     private fun convertToPaymentOrderRefundedEnvelope(evt: OutboxEvent): EventEnvelope<com.dogancaglar.paymentservice.application.events.PaymentOrderRefunded> {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, com.dogancaglar.paymentservice.application.events.PaymentOrderRefunded::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<com.dogancaglar.paymentservice.application.events.PaymentOrderRefunded>
        return envelope
    }
}
