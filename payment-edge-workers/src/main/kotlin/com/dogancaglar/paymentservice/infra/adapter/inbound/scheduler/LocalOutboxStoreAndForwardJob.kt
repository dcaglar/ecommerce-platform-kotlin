package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxForwarderPort
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxStoreAndForwardPort

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.DependsOn
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * LocalOutboxStoreAndForwardJob - The Edge Local Forwarder Job.
 * Created from scratch as a forwarder to replace the original OutboxDispatcherJob's dispatcher role.
 * 
 * Instead of publishing directly to Kafka, this job polls the local edge database,
 * extracts the aggregate ID (sellerId) to ensure order-preserving key-grouping,
 * forwards batches to the Central DB staging queue (modification_processor_queue),
 * and updates the edge watermark.
 */
@Service
@DependsOn("localOutboxMaintenanceJob")
class LocalOutboxStoreAndForwardJob(
    @param:Qualifier("localOutboxStoreAndForwardPort") private val localOutboxStoreAndForwardPort: LocalOutboxStoreAndForwardPort,
    private val centralOutboxRepository: CentralOutboxForwarderPort,
    @param:Qualifier("outboxJobTaskScheduler") private val taskScheduler: ThreadPoolTaskScheduler,
    @param:Value("\${outbox-dispatcher.thread-count:2}") private val threadCount: Int,
    @param:Value("\${outbox-dispatcher.batch-size:250}") private val batchSize: Int,
    @param:Value("\${app.instance-id}") private val appInstanceId: String,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        io.micrometer.core.instrument.Gauge.builder("local_outbox_backlog_size", this) {
            localOutboxStoreAndForwardPort.countNew().toDouble()
        }.register(meterRegistry)
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 5000)
    fun dispatchBatches() {
        if(centralOutboxRepository.isSchemaReady()) {
            repeat(threadCount) { workerIdx ->
                val delayMs = 500L * workerIdx
                taskScheduler.schedule(
                    {
                        dispatchBatchWorker()
                    },
                    Utc.nowInstant().plusMillis(delayMs)
                )
            }
        } else {
            logger.warn("FATAL ERROR, EDGE TABLE MOT PRESENT,SHUUTTING DOWN")
        }

    }

    @Scheduled(initialDelay = 30000, fixedDelay = 120000)
    @Transactional(transactionManager = "outboxTxManager", timeout = 5)
    fun reclaimStuck() {
        val reclaimed = localOutboxStoreAndForwardPort
            .reclaimStuck(60 * 10)
        if (reclaimed > 0) {
            logger.warn("Reclaimer reset {} stuck outbox events to NEW", reclaimed)
        }
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 2)
    fun claimBatch(batchSize: Int, workerId: String): List<OutboxEvent> {
        return localOutboxStoreAndForwardPort.findEligible(batchSize, workerId)
    }

    @Transactional(transactionManager = "centralTxManager", timeout = 5)
    fun forwardBatch(events: List<OutboxEvent>): Boolean {
        if (events.isEmpty()) return true

        return try {
            // Push standard OutboxEvents to central staging database
            centralOutboxRepository.insertBatch(appInstanceId, events)

            // Update the Edge Watermark progress
            val maxOriginatedAt = events.maxOf { Utc.toInstant(it.createdAt) }
            centralOutboxRepository.updateWatermark(appInstanceId, maxOriginatedAt)

            true
        } catch (t: Throwable) {
            logger.warn(
                "⚠️ Batch forward failed; will unclaim {} rows.",
                events.size, t
            )
            false
        }
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 5)
    fun persistResults(succeeded: List<OutboxEvent>) {
        if (succeeded.isNotEmpty()) {
            localOutboxStoreAndForwardPort.markDispatched(succeeded)
        }
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 2)
    fun unclaimFailedNow(workerId: String, failed: List<OutboxEvent>) {
        if (failed.isEmpty()) return
        val adapter = localOutboxStoreAndForwardPort
        val n = adapter.unclaimFailed(workerId, failed.map { it.oeid })
        if (n > 0) {
            logger.warn("Unclaimed {} failed outbox rows for worker={}", n, workerId)
        }
    }

    fun dispatchBatchWorker() {
        val sample = Timer.start(meterRegistry)
        val threadName = Thread.currentThread().name
        val workerId = "$appInstanceId:$threadName"

        val events = claimBatch(batchSize, workerId)
        if (events.isEmpty()) {
            centralOutboxRepository.updateWatermark(appInstanceId, Utc.nowInstant())
            sample.stop(meterRegistry.timer("outbox_dispatcher_duration"))
            return
        }

        val success = forwardBatch(events)
        if (success) {
            persistResults(events.map { it.markAsSent() })
            logger.info("Forwarded ok={} on {}", events.size, threadName)
            meterRegistry.counter("outbox_dispatched_total").increment(events.size.toDouble())
        } else {
            try {
                unclaimFailedNow(workerId, events)
            } catch (t: Throwable) {
                logger.warn("Unclaim failed for {} rows (worker={}) – will rely on reclaimer",
                    events.size, workerId, t)
            }
            logger.info("Forwarded failed={} on {}", events.size, threadName)
            meterRegistry.counter("outbox_dispatch_failed_total").increment(events.size.toDouble())
        }
        sample.stop(meterRegistry.timer("outbox_dispatcher_duration"))
    }

    @jakarta.annotation.PreDestroy
    fun onShutdown() {
        logger.info("Graceful shutdown initiated. Flushing remaining local outbox events to Central DB...")
        var flushCount = 0
        var emptyCycles = 0
        while (emptyCycles < 3) {
            val workerId = "$appInstanceId:shutdown-flush"
            val events = claimBatch(batchSize, workerId)
            if (events.isEmpty()) {
                emptyCycles++
                Thread.sleep(1000)
                continue
            }
            emptyCycles = 0
            val success = forwardBatch(events)
            if (success) {
                persistResults(events.map { it.markAsSent() })
                flushCount += events.size
            } else {
                logger.warn("Shutdown flush failed. Will retry.")
                unclaimFailedNow(workerId, events)
                Thread.sleep(2000)
            }
        }
        logger.info("Shutdown flush complete. Flushed {} events. Deleting watermark for node: {}", flushCount, appInstanceId)
        try {
            centralOutboxRepository.deleteWatermark(appInstanceId)
        } catch (t: Throwable) {
            logger.error("Failed to delete watermark during shutdown!", t)
        }
    }
}
