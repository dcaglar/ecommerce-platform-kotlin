package com.dogancaglar.infrastructure.redis

import com.dogancaglar.infrastructure.adapter.producers.PaymentEventPublisher
import com.dogancaglar.payment.application.events.EventMetadatas
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class RetryDispatcherScheduler(
    private val paymentRetryQueueAdapter: PaymentRetryQueueAdapter,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val meterRegistry: MeterRegistry
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
            // You can set the batch size cap here, e.g., 1000
            val dueEnvelopes =
                paymentRetryQueueAdapter.pollDueRetries(1000) // adjust if you have pollDueRetriesAtomic(max)
            batchSize.set(dueEnvelopes.size.toDouble())

            // Metrics: counters for processed and failed
            val processedCounter = meterRegistry.counter(
                "redis_retry_events_processed_total"
            )
            val failedCounter = meterRegistry.counter(
                "redis_retry_events_failed_total"
            )

            dueEnvelopes.forEach { envelope ->
                try {
                    paymentEventPublisher.publish(
                        preSetEventIdFromCaller = envelope.eventId,
                        aggregateId = envelope.aggregateId,
                        eventMetaData = EventMetadatas.PaymentOrderRetryRequestedMetadata,
                        data = envelope.data,
                        parentEventId = envelope.parentEventId,
                        traceId = envelope.traceId
                    )
                    processedCounter.increment()
                } catch (e: Exception) {
                    failedCounter.increment()
                    logger.error("Failed to dispatch retry event: ${e.message}", e)
                }
            }
        })
    }
}