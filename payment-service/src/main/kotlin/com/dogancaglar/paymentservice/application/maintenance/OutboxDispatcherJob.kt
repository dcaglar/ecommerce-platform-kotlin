package com.dogancaglar.paymentservice.application.maintenance


import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.events.OutboxEvent
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCHED_TOTAL
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCHER_DURATION
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCH_FAILED_TOTAL
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_EVENT_BACKLOG
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventPort
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class OutboxDispatcherJob(
    private val outboxEventPort: OutboxEventPort,
    private val paymentEventPublisher: EventPublisherPort,
    private val kafkaTx: KafkaTxExecutor,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
    @Qualifier("outboxTaskScheduler") private val taskScheduler: ThreadPoolTaskScheduler,
    @Value("\${outbox-dispatcher.thread-count:8}") private val threadCount: Int,
    @Value("\${outbox-dispatcher.batch-size:250}") private val batchSize: Int,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val backlog = java.util.concurrent.atomic.AtomicLong(0)

    init {
        Gauge.builder(OUTBOX_EVENT_BACKLOG) { backlog.get().toDouble() }
            .description("Outbox events waiting to be dispatched (status=NEW)")
            .strongReference(true)
            .register(meterRegistry)
        // ❌ removed pre-registration of counters/timers
    }

    @Scheduled(fixedDelay = 5000)
    fun refreshBacklogGauge() {
        backlog.set(outboxEventPort.countByStatus("NEW"))
    }

    @Scheduled(fixedDelay = 5000)
    fun dispatchBatches() {
        repeat(threadCount) { workerId ->
            val delayMs = 500L * workerId
            taskScheduler.schedule(
                { dispatchBatchWorker() },  // no need to pass workerId unless you want it in logs
                java.time.Instant.now(clock).plusMillis(delayMs)
            )
        }
    }

    @Transactional
    fun dispatchBatchWorker() {
        val start = System.currentTimeMillis()
        val threadName = Thread.currentThread().name

        val events = outboxEventPort.findBatchForDispatch(batchSize)
        if (events.isEmpty()) {
            logger.debug("No events to dispatch on {}", threadName)
            return
        }

        val succeeded = mutableListOf<OutboxEvent>()
        val failed = mutableListOf<OutboxEvent>()

        for (event in events) {
            try {
                val envelopeType = objectMapper.typeFactory
                    .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)
                val env: EventEnvelope<PaymentOrderCreated> = objectMapper.readValue(event.payload, envelopeType)

                LogContext.with(env) {
                    kafkaTx.run {
                        paymentEventPublisher.publishSync(
                            preSetEventIdFromCaller = env.eventId,
                            aggregateId = env.aggregateId,
                            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                            data = env.data,
                            traceId = env.traceId,
                            parentEventId = env.parentEventId
                        )
                    }
                }
                event.markAsSent()
                succeeded += event
            } catch (ex: Exception) {
                failed += event
                logger.error("Failed to publish event {} on {}: {}", event.oeid, threadName, ex.message, ex)
            }
        }

        if (succeeded.isNotEmpty()) {
            outboxEventPort.updateAll(succeeded)
            meterRegistry.counter(OUTBOX_DISPATCHED_TOTAL, "thread", threadName)
                .increment(succeeded.size.toDouble())
        }
        if (failed.isNotEmpty()) {
            meterRegistry.counter(OUTBOX_DISPATCH_FAILED_TOTAL, "thread", threadName)
                .increment(failed.size.toDouble())
        }

        val durationMs = System.currentTimeMillis() - start
        meterRegistry.timer(OUTBOX_DISPATCHER_DURATION, "thread", threadName)
            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)

        logger.info("Dispatched ok={} fail={} on {}", succeeded.size, failed.size, threadName)
    }
}