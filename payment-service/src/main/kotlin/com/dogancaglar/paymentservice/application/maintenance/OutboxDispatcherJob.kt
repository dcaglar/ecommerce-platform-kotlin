package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.events.OutboxEvent
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCHED_TOTAL
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCHER_DURATION
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCH_FAILED_TOTAL
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_EVENT_BACKLOG
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventPort
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit

@Service
class OutboxDispatcherJob(
    private val outboxEventPort: OutboxEventPort, // <-- use the shared outbox event port bean
    private val paymentEventPublisher: EventPublisherPort,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
    @Qualifier("outboxTaskScheduler") // <-- use the shared scheduler bean
    private val taskScheduler: ThreadPoolTaskScheduler, // <-- inject the shared scheduler!
    @Value("\${outbox-dispatcher.thread-count:8}")
    private val threadCount: Int,
    @Value("\${outbox-dispatcher.batch-size:250}")
    private val batchSize: Int,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    fun dispatchBatches() {
        logger.debug("Starting outbox event dispatch batches ")
        repeat(threadCount) { workerId ->
            val delayMs = 500 * workerId // e.g. 0, 300, 600, ...
            taskScheduler.schedule({
                dispatchBatchWorker(workerId)
            }, Instant.now(clock).plusMillis(delayMs.toLong()))
        }
    }

    @Transactional
    fun dispatchBatchWorker(workedId: Int) {
        val start = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        val workerContext = mapOf("workerId" to workedId.toString(), "threadName" to threadName)
        LogContext.with(workerContext) {
            val events = outboxEventPort.findBatchForDispatch(batchSize)
            logger.info("Found \\${events.size} events to dispatch in worker $workedId on $threadName")
            if (events.isEmpty()) {
                logger.debug("No events to dispatch in worker $workedId, exiting. on $threadName")
                return@with
            }

            val succeeded = mutableListOf<OutboxEvent>()
            val failed = mutableListOf<OutboxEvent>()

            for (event in events) {
                try {
                    //todo do we have to set LogContext here?
                    val envelopeType = objectMapper.typeFactory
                        .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)
                    val envelope: EventEnvelope<PaymentOrderCreated> =
                        objectMapper.readValue(event.payload, envelopeType)
                    LogContext.with(envelope) {
                        paymentEventPublisher.publish(
                            preSetEventIdFromCaller = envelope.eventId,
                            aggregateId = envelope.aggregateId,
                            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                            data = envelope.data,
                            traceId = envelope.traceId,
                            parentEventId = envelope.eventId
                        )
                        logger.info("Dispatcher job Published event ${event.oeid} in worker $workedId")
                    }
                    event.markAsSent()
                    succeeded.add(event)
                } catch (ex: Exception) {
                    failed.add(event)
                    logger.error("Failed to publish event on $threadName ${event.oeid}: ${ex.message}", ex)
                    // Optionally log the error for monitoring
                    // logger.error("Failed to publish event ${event.id}", ex)
                }
            }
            try {
                // Only persist the succeeded events
                if (succeeded.isNotEmpty()) {
                    outboxEventPort.updateAll(succeeded)
                    meterRegistry.counter(OUTBOX_DISPATCHED_TOTAL).increment(succeeded.size.toDouble())
                    logger.debug("Successfully dispatched ${succeeded.size} events in worker $workedId on $threadName")
                } else {
                    logger.warn("No events were successfully dispatched in worker $workedId on $threadName")
                }

                if (failed.isNotEmpty()) {
                    logger.warn("Failed to dispatch ${failed.size} events in worker $workedId")
                    meterRegistry.counter(OUTBOX_DISPATCH_FAILED_TOTAL).increment(failed.size.toDouble())
                }
                // Update backlog gauge
                meterRegistry.gauge(OUTBOX_EVENT_BACKLOG, outboxEventPort) { _ ->
                    outboxEventPort.countByStatus("NEW").toDouble()
                }
                val end = System.currentTimeMillis()
                val durationMs = end - start
                logger.info("DispatchBatchWorker #$workedId completed in ${durationMs}ms on $threadName")
                // Optionally, record as a metric:
                meterRegistry.timer(OUTBOX_DISPATCHER_DURATION, "thread", threadName)
                    .record(durationMs, TimeUnit.MILLISECONDS)
            } catch (ex: Exception) {
                logger.error("Exception in OutboxDispatcherJob worker $workedId on $threadName", ex)
                throw ex
            }
        }
    }
}