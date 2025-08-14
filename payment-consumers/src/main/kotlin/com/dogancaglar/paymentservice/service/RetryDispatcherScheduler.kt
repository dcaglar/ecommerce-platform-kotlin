package com.dogancaglar.paymentservice.service

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.outbound.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.outbound.redis.PaymentRetryQueueAdapter
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
import io.micrometer.core.instrument.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class RetryDispatcherScheduler(
    private val retryQueue: PaymentRetryQueueAdapter,
    private val publisher: PaymentEventPublisher,
    private val meterRegistry: MeterRegistry,
    private val kafkaTx: KafkaTxExecutor
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val batchSize = AtomicInteger(0)

    // Static tags to make dashboards easier
    private val topicTag = Tag.of("topic", EventMetadatas.PaymentOrderPspCallRequestedMetadata.topic)

    // Create meters once
    private val processedCounter: Counter =
        Counter.builder("redis_retry_events_total")
            .description("Total retry events successfully re-published")
            .tag("result", "processed")
            .tags(listOf(topicTag))
            .register(meterRegistry)

    private val failedCounter: Counter =
        Counter.builder("redis_retry_events_total")
            .description("Total retry events that failed to re-publish")
            .tag("result", "failed")
            .tags(listOf(topicTag))
            .register(meterRegistry)

    private val batchTimer: Timer =
        Timer.builder("redis_retry_dispatch_batch_seconds")
            .description("Total time to dispatch a retry batch")
            .publishPercentiles(0.5, 0.95, 0.99)
            .tags(listOf(topicTag))
            .register(meterRegistry)

    private val perEventTimer: Timer =
        Timer.builder("redis_retry_dispatch_event_seconds")
            .description("Time to publish a single retry event")
            .publishPercentiles(0.5, 0.95, 0.99)
            .tags(listOf(topicTag))
            .register(meterRegistry)

    init {
        // Gauge = last batch size; registered once
        Gauge.builder("redis_retry_batch_size") { batchSize.get().toDouble() }
            .description("Number of retry events processed in the last batch")
            .tags(listOf(topicTag))
            .register(meterRegistry)
    }

    @Scheduled(fixedDelay = 5_000)
    fun dispatch() {
        val batchSample = Timer.start(meterRegistry)

        var success = 0
        var fail = 0

        val due: List<EventEnvelope<PaymentOrderPspCallRequested>> =
            retryQueue.pollDueRetries(1000)

        batchSize.set(due.size)

        for (env in due) {
            val evtSample = Timer.start(meterRegistry)
            try {
                kafkaTx.run {
                    publisher.publish(
                        preSetEventIdFromCaller = env.eventId,
                        aggregateId = env.aggregateId,
                        eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
                        data = env.data,
                        parentEventId = env.parentEventId,
                        traceId = env.traceId
                    )
                }
                success++
            } catch (e: Exception) {
                fail++
                logger.error(
                    "Failed to dispatch PSP retry envelope agg={} id={}: {}",
                    env.aggregateId, env.eventId, e.message, e
                )
            } finally {
                evtSample.stop(perEventTimer)
            }
        }

        // one increment per batch → cheaper & cleaner graphs
        if (success > 0) processedCounter.increment(success.toDouble())
        if (fail > 0) failedCounter.increment(fail.toDouble())

        batchSample.stop(batchTimer)
    }
}