package com.dogancaglar.paymentservice.service

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.outbound.redis.PaymentRetryQueueAdapter
import com.dogancaglar.paymentservice.adapter.outbound.redis.PaymentRetryRedisCache
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.PaymentOrderRetryRequested
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class RetryDispatcherScheduler(
    private val paymentRetryQueueAdapter: PaymentRetryQueueAdapter,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val meterRegistry: MeterRegistry,
    private val kafkaTx: KafkaTxExecutor,
    private val paymentRetryRedisCache: PaymentRetryRedisCache,
    @Qualifier("myObjectMapper") private val objectMapper: ObjectMapper

) {
    private val logger = LoggerFactory.getLogger(RetryDispatcherScheduler::class.java)

    // Use an AtomicReference for the current batch size metric (updated each run)
    private val batchSize = AtomicReference(0.0)

    init {
        // Expose current batch size gauge (live view)
        Gauge.builder("redis_retry_batch_size", batchSize, AtomicReference<Double>::get)
            .description("Number of retry events processed in last dispatch batch")
            .register(meterRegistry)
    }

    @Scheduled(fixedDelay = 5000)
    fun dispatchPaymentOrderRetriesViaRedisQueue() {
        meterRegistry.timer("redis_retry_dispatch_execution_seconds").record(Runnable {
            val dueEnvelopes = paymentRetryQueueAdapter.pollDueRetries(1000)
            batchSize.set(dueEnvelopes.size.toDouble())

            val processedCounter = meterRegistry.counter("redis_retry_events_processed_total")
            val failedCounter = meterRegistry.counter("redis_retry_events_failed_total")

            // One TX per message (simple & safe). You could batch if needed.
            dueEnvelopes.forEach { envelope ->
                try {
                    kafkaTx.run {
                        paymentEventPublisher.publish(
                            preSetEventIdFromCaller = envelope.eventId,
                            aggregateId = envelope.aggregateId,
                            eventMetaData = EventMetadatas.PaymentOrderRetryRequestedMetadata,
                            data = envelope.data,
                            parentEventId = envelope.parentEventId,
                            traceId = envelope.traceId
                        )
                    }
                    processedCounter.increment()
                } catch (e: Exception) {
                    failedCounter.increment()
                    // 🔁 Put it back so we don't lose it if the Kafka TX aborted
                    rescheduleExisting(envelope, 5_000)
                    logger.error("Failed to dispatch retry event: ${e.message}", e)
                }
            }

        })
    }

    fun rescheduleExisting(
        envelope: EventEnvelope<PaymentOrderRetryRequested>,
        delayMs: Long = 5_000
    ) {
        val json = objectMapper.writeValueAsString(envelope)
        val retryAt = System.currentTimeMillis() + delayMs
        paymentRetryRedisCache.scheduleRetry(json, retryAt.toDouble())
    }


}
