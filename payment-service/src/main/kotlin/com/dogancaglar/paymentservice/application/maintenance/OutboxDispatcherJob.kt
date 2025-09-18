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
    private val clock: Clock,
    @Value("\${outbox-backlog.resync-interval:PT5M}") private val backlogResyncInterval: String


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
        try {
            backlog.set(outboxEventPort.countByStatus("NEW"))
            logger.info("Seeded outbox backlog gauge with initial NEW count={}", backlog.get())
        } catch (e: Exception) {
            logger.warn("Failed to seed backlog gauge; will rely on deltas until next resync: {}", e.message, e)
        }
    }

    /** Slow, optional drift-correct resync (disable with outbox-backlog.resync-interval=PT0S). */
    @Scheduled(fixedDelayString = "\${outbox-backlog.resync-interval:PT5M}")
    fun slowResyncBacklog() {
        if (backlogResyncInterval == "PT0S") return
        try {
            val fresh = outboxEventPort.countByStatus("NEW")
            backlog.set(fresh)
            logger.debug("Backlog slow-resync set to {}", fresh)
        } catch (e: Exception) {
            logger.warn("Backlog slow-resync failed: {}", e.message)
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
            backlog.addAndGet(reclaimed.toLong()) // ⬅️ keep gauge in sync
        }
    }



    @Transactional(transactionManager = "outboxTxManager", timeout = 2)
    fun claimBatch(batchSize: Int, workerId: String): List<OutboxEvent> {
        // UPDATE ... WHERE status='NEW' ... FOR UPDATE SKIP LOCKED RETURNING ...
        val claimed = (outboxEventPort as OutboxJobMyBatisAdapter)
            .findBatchForDispatch(batchSize, workerId)
        // Claiming moves rows NEW -> PROCESSING → decrease backlog estimate.
        if (claimed.isNotEmpty()) backlog.addAndGet(-claimed.size.toLong())
        return claimed
    }


    fun publishBatch(events: List<OutboxEvent>) :  Triple<List<OutboxEvent>, List<OutboxEvent>, List<OutboxEvent>> {
        val succeeded = mutableListOf<OutboxEvent>()
        val toUnclaim = mutableListOf<OutboxEvent>()
        val keepClaimed = mutableListOf<OutboxEvent>() // let reclaimer handle
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
            }catch (ex: Exception) {
                if (isTransientKafkaError(ex)) {
                    toUnclaim += event
                    logger.warn("Transient publish failure; will UNCLAIM event {}: {}", event.oeid, ex.toString())
                } else {
                    keepClaimed += event
                    logger.error("Permanent-ish publish failure; KEEPING CLAIMED event {} (reclaimer will handle): {}",
                        event.oeid, ex.toString())
                }
                logger.error("Failed to publish event {}: {}", event.oeid, ex.message, ex)
            }
        }
        return Triple(succeeded, toUnclaim, keepClaimed)
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 5)
    fun persistResults(succeeded: List<OutboxEvent>) {
        if (succeeded.isNotEmpty()) {
            outboxEventPort.updateAll(succeeded) // clears claimed_* and sets SENT
        }
    }



    /** Tiny, isolated DB txn that frees failed rows immediately. */
    @Transactional(transactionManager = "outboxTxManager", timeout = 2)
    fun unclaimFailedNow(workerId: String, failed: List<OutboxEvent>) {
        if (failed.isEmpty()) return
        val adapter = (outboxEventPort as OutboxJobMyBatisAdapter)
        val n = adapter.unclaimSpecific(workerId, failed.map { it.oeid })
        if (n > 0) {
            logger.warn("Unclaimed {} failed outbox rows for worker={}", n, workerId)
            // Unclaiming moves rows PROCESSING -> NEW → increase backlog estimate.
            backlog.addAndGet(n.toLong())
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

        val (succeeded, toUnclaim,keepClaimed) = publishBatch(events)
        persistResults(succeeded)

        try {
            unclaimFailedNow(workerId, toUnclaim)
        } catch (t: Throwable) {
            logger.warn("Unclaim failed for {} rows (worker={}) – will rely on reclaimer", toUnclaim.size, workerId, t)
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
    private fun rootCause(t: Throwable): Throwable {
        var c: Throwable = t
        while (c.cause != null && c.cause !== c) c = c.cause!!
        return c
    }

    private fun isTransientKafkaError(t: Throwable): Boolean {
        val rc = rootCause(t)
        return rc is java.util.concurrent.TimeoutException ||
                rc is org.apache.kafka.common.errors.RetriableException ||
                rc is org.apache.kafka.common.errors.TransactionAbortedException
    }

}