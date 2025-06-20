package com.dogancaglar.paymentservice.adapter.outbox.producer

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.adapter.kafka.producers.PaymentEventPublisher
import com.dogancaglar.paymentservice.application.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.config.messaging.EventMetadatas
import com.dogancaglar.paymentservice.config.metrics.MetricNames.OUTBOX_DISPATCHED_TOTAL
import com.dogancaglar.paymentservice.config.metrics.MetricNames.OUTBOX_DISPATCHER_DURATION
import com.dogancaglar.paymentservice.config.metrics.MetricNames.OUTBOX_DISPATCH_FAILED_TOTAL
import com.dogancaglar.paymentservice.config.metrics.MetricNames.OUTBOX_EVENT_BACKLOG
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxDispatcherService(
    private val outboxEventPort: OutboxEventPort,
    private val paymentEventPublisher: PaymentEventPublisher,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
    @Qualifier("outboxTaskScheduler") // <-- use the shared scheduler bean
    private val taskScheduler: ThreadPoolTaskScheduler // <-- inject the shared scheduler!
) {
    companion object {
        private const val THREAD_COUNT = 6
        private const val BATCH_SIZE = 500
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 10_000)
    fun dispatchBatches() {
        logger.info("Starting outbox event dispatch batches ")
        repeat(THREAD_COUNT) {
            logger.info("Starting outbox event dispatch worker #$it")
            taskScheduler.submit { dispatchBatchWorker(it) }
        }
    }

    @Transactional
    fun dispatchBatchWorker(workedId: Int) {
        val start = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        logger.info("Started dispatchBatchWorker method for $workedId on $threadName ")
        val events = outboxEventPort.findBatchForDispatch("NEW", BATCH_SIZE)
        logger.info("Found ${events.size} events to dispatch in worker $workedId on $threadName")
        if (events.isEmpty()) {
            logger.info("No events to dispatch in worker $workedId, exiting. on $threadName")
            return
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

                paymentEventPublisher.publish(
                    preSetEventIdFromCaller = envelope.eventId,
                    aggregateId = envelope.aggregateId,
                    eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                    data = envelope.data,
                    traceId = envelope.traceId,
                    parentEventId = envelope.eventId
                )
                event.markAsSent()
                succeeded.add(event)
            } catch (ex: Exception) {
                failed.add(event)
                logger.error("Failed to publish event on $threadName ${event.eventId}: ${ex.message}", ex)
                // Optionally log the error for monitoring
                // logger.error("Failed to publish event ${event.id}", ex)
            }
        }
        // Only persist the succeeded events
        if (succeeded.isNotEmpty()) {
            outboxEventPort.saveAll(succeeded)
            meterRegistry.counter(OUTBOX_DISPATCHED_TOTAL).increment(succeeded.size.toDouble())
            logger.info("Successfully dispatched ${succeeded.size} events in worker $workedId on $threadName")
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
            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
}