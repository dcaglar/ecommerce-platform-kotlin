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
import org.springframework.context.annotation.DependsOn
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
@DependsOn( "outboxPartitionCreator")
class OutboxDispatcherJob(
    @Qualifier("outboxJobPort") private val outboxEventPort: OutboxEventPort,
    private val paymentEventPublisher: EventPublisherPort,
    private val kafkaTx: KafkaTxExecutor,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
    @Qualifier("outboxJobTaskScheduler") private val taskScheduler: ThreadPoolTaskScheduler,
    @Value("\${outbox-dispatcher.thread-count:2}") private val threadCount: Int,
    @Value("\${outbox-dispatcher.batch-size:250}") private val batchSize: Int,
    @Value("\${app.instance-id}") private val appInstanceId: String,
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
        repeat(threadCount) { workerIdx ->
            val delayMs = 500L * workerIdx
            taskScheduler.schedule({ dispatchBatchWorker() },
                java.time.Instant.now(clock).plusMillis(delayMs))
        }
    }


    /** Reclaimer — runs every minute (tune as you like) */
    @Scheduled(fixedDelay = 60_000)
    @Transactional(transactionManager = "outboxTxManager", timeout = 5)
    fun reclaimStuck() {
        val reclaimed = (outboxEventPort as OutboxJobMyBatisAdapter)
            .reclaimStuckClaims(60 * 10) // reclaim if claimed > 10 minutes ago
        if (reclaimed > 0) {
            logger.warn("Reclaimer reset {} stuck outbox events to NEW", reclaimed)
        }
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 2)
    fun claimBatch(batchSize: Int, workerId: String): List<OutboxEvent> {
        // This does the UPDATE ... SKIP LOCKED ... RETURNING
        return (outboxEventPort as OutboxJobMyBatisAdapter)
            .findBatchForDispatch(batchSize, workerId)
    }

    fun publishBatch(events: List<OutboxEvent>) : Pair<List<OutboxEvent>, List<OutboxEvent>> {
        val succeeded = mutableListOf<OutboxEvent>()
        val failed = mutableListOf<OutboxEvent>()

        for (event in events) {
            try {
                val envType = objectMapper.typeFactory
                    .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)
                val env: EventEnvelope<PaymentOrderCreated> = objectMapper.readValue(event.payload, envType)

                LogContext.with(env) {
                    // Kafka producer transaction only (kafkaTx.run)
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
                logger.error("Failed to publish event {}: {}", event.oeid, ex.message, ex)
            }
        }
        return succeeded to failed
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 5)
    fun persistResults(succeeded: List<OutboxEvent>) {
        if (succeeded.isNotEmpty()) {
            outboxEventPort.updateAll(succeeded) // clears claimed_* and sets SENT
        }
    }


    fun dispatchBatchWorker() {
        val start = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        val workerId = "$appInstanceId:$threadName"                    // ⬅️ canonical worker id


        val events = claimBatch(batchSize, workerId)
        if (events.isEmpty()) {
            logger.warn("No events to dispatch on {}", threadName)
            return
        }

        val (succeeded, failed) = publishBatch(events)
        persistResults(succeeded)

        if (succeeded.isNotEmpty()) {
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