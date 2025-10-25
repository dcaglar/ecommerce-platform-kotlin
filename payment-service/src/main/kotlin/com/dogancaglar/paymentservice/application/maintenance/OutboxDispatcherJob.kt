package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.OutboxEvent
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCHED_TOTAL
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCHER_DURATION
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCH_FAILED_TOTAL
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_EVENT_BACKLOG
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventPort
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
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
@DependsOn("outboxPartitionCreator")
class OutboxDispatcherJob(
    @param:Qualifier("outboxJobPort") private val outboxEventPort: OutboxEventPort,
    @param:Qualifier("batchPaymentEventPublisher") private val syncPaymentEventPublisher: EventPublisherPort,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
    @param:Qualifier("outboxJobTaskScheduler") private val taskScheduler: ThreadPoolTaskScheduler,
    @param:Value("\${outbox-dispatcher.thread-count:2}") private val threadCount: Int,
    @param:Value("\${outbox-dispatcher.batch-size:250}") private val batchSize: Int,
    @param:Value("\${app.instance-id}") private val appInstanceId: String,
    private val clock: Clock,
    @param:Value("\${outbox-backlog.resync-interval:PT5M}") private val backlogResyncInterval: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val backlog = java.util.concurrent.atomic.AtomicLong(0)

    init {
        Gauge.builder(OUTBOX_EVENT_BACKLOG) { backlog.get().toDouble() }
            .description("Estimated NEW outbox events (in-memory, delta-updated)")
            .strongReference(true)
            .register(meterRegistry)
    }

    @PostConstruct
    fun seedBacklogOnce() {
        resetBacklogFromDb("initial seed")
    }

    /** Periodic drift correction */
    @Scheduled(fixedDelayString = "\${outbox-backlog.resync-interval:PT5M}")
    fun slowResyncBacklog() {
        if (backlogResyncInterval == "PT0S") return
        resetBacklogFromDb("slow resync")
    }

    private fun resetBacklogFromDb(reason: String) {
        try {
            val fresh = outboxEventPort.countByStatus("NEW")
            backlog.set(fresh.toLong())
            logger.info("Backlog gauge reset to {} ({})", fresh, reason)
        } catch (e: Exception) {
            logger.warn("Failed to reset backlog gauge ({}): {}", reason, e.message)
        }
    }

    @Scheduled(fixedDelay = 5000)
    fun dispatchBatches() {
        repeat(threadCount) { workerIdx ->
            val delayMs = 500L * workerIdx
            taskScheduler.schedule({ dispatchBatchWorker() },
                java.time.Instant.now(clock).plusMillis(delayMs))
        }
    }

    @Scheduled(fixedDelay = 120000)
    @Transactional(transactionManager = "outboxTxManager", timeout = 5)
    fun reclaimStuck() {
        val reclaimed = (outboxEventPort as OutboxJobMyBatisAdapter)
            .reclaimStuckClaims(60 * 10)
        if (reclaimed > 0) {
            logger.warn("Reclaimer reset {} stuck outbox events to NEW", reclaimed)
            backlogAdd(reclaimed.toLong())
        }
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 2)
    fun claimBatch(batchSize: Int, workerId: String): List<OutboxEvent> {
        val claimed = (outboxEventPort as OutboxJobMyBatisAdapter)
            .findBatchForDispatch(batchSize, workerId)
        if (claimed.isNotEmpty()) backlogAdd(-claimed.size.toLong())
        return claimed
    }

    fun publishBatch(events: List<OutboxEvent>)
            : Triple<List<OutboxEvent>, List<OutboxEvent>, List<OutboxEvent>> {
        if (events.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())
        return try {
            val envType = objectMapper.typeFactory
                .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)
            @Suppress("UNCHECKED_CAST")
            val envelopes: List<EventEnvelope<PaymentOrderCreated>> =
                events.map { evt -> objectMapper.readValue(evt.payload, envType) as EventEnvelope<PaymentOrderCreated> }

            val ok = syncPaymentEventPublisher.publishBatchAtomically(
                envelopes = envelopes,
                eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                timeout = java.time.Duration.ofSeconds(30)
            )

            if (ok) {
                val succeeded = events.onEach { it.markAsSent() }
                Triple(succeeded, emptyList(), emptyList())
            } else {
                Triple(emptyList(), events.toList(), emptyList())
            }
        } catch (t: Throwable) {
            logger.warn("Batch publish aborted; will UNCLAIM {} rows: {}", events.size, t.toString())
            Triple(emptyList(), events.toList(), emptyList())
        }
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 5)
    fun persistResults(succeeded: List<OutboxEvent>) {
        if (succeeded.isNotEmpty()) {
            outboxEventPort.updateAll(succeeded)
        }
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 2)
    fun unclaimFailedNow(workerId: String, failed: List<OutboxEvent>) {
        if (failed.isEmpty()) return
        val adapter = (outboxEventPort as OutboxJobMyBatisAdapter)
        val n = adapter.unclaimSpecific(workerId, failed.map { it.oeid })
        if (n > 0) {
            logger.warn("Unclaimed {} failed outbox rows for worker={}", n, workerId)
            backlogAdd(n.toLong())
        }
    }

    fun dispatchBatchWorker() {
        val start = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        val workerId = "$appInstanceId:$threadName"

        val events = claimBatch(batchSize, workerId)
        if (events.isEmpty()) {
            logger.debug("No events to dispatch on {}", threadName)
            return
        }

        val (succeeded, toUnclaim, keepClaimed) = publishBatch(events)
        persistResults(succeeded)

        try {
            unclaimFailedNow(workerId, toUnclaim)
        } catch (t: Throwable) {
            logger.warn("Unclaim failed for {} rows (worker={}) – will rely on reclaimer",
                toUnclaim.size, workerId, t)
        }

        if (succeeded.isNotEmpty()) {
            meterRegistry.counter(OUTBOX_DISPATCHED_TOTAL, "thread", threadName)
                .increment(succeeded.size.toDouble())
        }
        val failed = toUnclaim + keepClaimed
        if (failed.isNotEmpty()) {
            meterRegistry.counter(OUTBOX_DISPATCH_FAILED_TOTAL, "thread", threadName)
                .increment(failed.size.toDouble())
        }

        val durationMs = System.currentTimeMillis() - start
        meterRegistry.timer(OUTBOX_DISPATCHER_DURATION, "thread", threadName)
            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)

        logger.info("Dispatched ok={} fail={} on {}", succeeded.size, failed.size, threadName)
    }

    /** Adjust backlog but never let it go below zero. */
    private fun backlogAdd(delta: Long) {
        backlog.updateAndGet { curr -> maxOf(0, curr + delta) }
    }
}