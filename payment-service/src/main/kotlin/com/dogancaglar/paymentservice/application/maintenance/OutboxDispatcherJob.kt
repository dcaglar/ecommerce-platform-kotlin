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

        // Pre-register per-worker meters so they exist before first dispatch
        for (w in 0 until threadCount) {
            meterRegistry.counter(OUTBOX_DISPATCHED_TOTAL, "worker", w.toString()).increment(0.0)
            meterRegistry.counter(OUTBOX_DISPATCH_FAILED_TOTAL, "worker", w.toString()).increment(0.0)
            meterRegistry.timer(OUTBOX_DISPATCHER_DURATION, "worker", w.toString())
                .record(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    @Scheduled(fixedDelay = 5000)
    fun refreshBacklogGauge() {
        backlog.set(outboxEventPort.countByStatus("NEW"))
    }


    @Scheduled(fixedDelay = 5000)
    fun dispatchBatches() {
        logger.debug("Starting outbox event dispatch batches")
        repeat(threadCount) { workerId ->
            val delayMs = 500L * workerId
            taskScheduler.schedule(
                { dispatchBatchWorker(workerId) },
                java.time.Instant.now(clock).plusMillis(delayMs)
            )
        }
    }

    @Transactional
    fun dispatchBatchWorker(workerId: Int) {
        val start = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        val workerContext = mapOf("workerId" to workerId.toString(), "threadName" to threadName)

        LogContext.with(workerContext) {
            val events = outboxEventPort.findBatchForDispatch(batchSize)
            logger.info("Found ${events.size} events to dispatch in worker $workerId on $threadName")
            if (events.isEmpty()) {
                logger.debug("No events to dispatch in worker $workerId on $threadName")
                return@with
            }

            val succeeded = mutableListOf<OutboxEvent>()
            val failed = mutableListOf<OutboxEvent>()

            for (event in events) {
                try {
                    val envelopeType = objectMapper.typeFactory
                        .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)
                    val envelope: EventEnvelope<PaymentOrderCreated> =
                        objectMapper.readValue(event.payload, envelopeType)

                    LogContext.with(envelope) {
                        kafkaTx.run {
                            paymentEventPublisher.publishSync(
                                preSetEventIdFromCaller = envelope.eventId,
                                aggregateId = envelope.aggregateId,
                                eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                                data = envelope.data,
                                traceId = envelope.traceId,
                                parentEventId = envelope.parentEventId
                            )
                        }
                        logger.info("Dispatcher job published event ${event.oeid} in worker $workerId")
                    }

                    event.markAsSent()
                    succeeded.add(event)
                } catch (ex: Exception) {
                    failed.add(event)
                    logger.error("Failed to publish event ${event.oeid} on $threadName: ${ex.message}", ex)
                }
            }

            try {
                // Persist only succeeded
                if (succeeded.isNotEmpty()) {
                    outboxEventPort.updateAll(succeeded)
                    meterRegistry.counter(OUTBOX_DISPATCHED_TOTAL, "worker", workerId.toString())
                        .increment(succeeded.size.toDouble())
                    logger.debug("Dispatched ${succeeded.size} events in worker $workerId on $threadName")
                } else {
                    logger.warn("No events were successfully dispatched in worker $workerId on $threadName")
                }

                if (failed.isNotEmpty()) {
                    meterRegistry.counter(OUTBOX_DISPATCH_FAILED_TOTAL, "worker", workerId.toString())
                        .increment(failed.size.toDouble())
                    logger.warn("Failed to dispatch ${failed.size} events in worker $workerId")
                }

                val durationMs = System.currentTimeMillis() - start
                meterRegistry.timer(OUTBOX_DISPATCHER_DURATION, "worker", workerId.toString(), "thread", threadName)
                    .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)

                logger.info(
                    "DispatchBatchWorker #{} completed in {}ms on {} (ok={}, fail={})",
                    workerId, durationMs, threadName, succeeded.size, failed.size
                )
            } catch (ex: Exception) {
                logger.error("Exception in OutboxDispatcherJob worker $workerId on $threadName", ex)
                throw ex
            }
        }
    }
}