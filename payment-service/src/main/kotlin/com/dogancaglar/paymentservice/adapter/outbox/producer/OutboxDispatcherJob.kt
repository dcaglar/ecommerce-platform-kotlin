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
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@Service
class OutboxDispatcherJob(
    private val outboxEventPort: OutboxEventPort,                     // master ( @Primary )
    @Qualifier("outboxEventReader") private val replicaPort: OutboxEventPort, // replica
    private val publisher: PaymentEventPublisher,
    private val meter: MeterRegistry,
    private val mapper: ObjectMapper,
    @Qualifier("outboxTaskScheduler") private val scheduler: ThreadPoolTaskScheduler,
    @Value("\${outbox-dispatcher.thread-count:8}") private val threads: Int,
    @Value("\${outbox-dispatcher.batch-size:2000}") private val batch: Int,
    private val clock: Clock
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /* ─────────── scheduler entry ─────────── */
    @Scheduled(fixedDelay = 5_000)
    fun dispatchLoops() =
        repeat(threads) { id ->
            scheduler.schedule(
                { worker(id) },
                Instant.now(clock).plusMillis((500L * id))
            )
        }

    /* ─────────── one worker pass ─────────── */
    fun worker(id: Int) {
        val start = System.currentTimeMillis()

        /* ① read cursor & poll replica */
        val cursor = replicaPort.read().atOffset(ZoneOffset.UTC)      // Instant → OffsetDateTime (UTC)
            .toLocalDateTime()
        val events = replicaPort.findBatchAfter(cursor, batch)
        if (events.isEmpty()) return

        /* ② publish */
        val ok = mutableListOf<OutboxEvent>();
        val ko = mutableListOf<OutboxEvent>()
        var maxTs = cursor

        events.forEach { ev ->
            try {
                val type = mapper.typeFactory
                    .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)
                val env: EventEnvelope<PaymentOrderCreated> = mapper.readValue(ev.payload, type)

                publisher.publish(
                    preSetEventIdFromCaller = env.eventId,
                    aggregateId = env.aggregateId,
                    eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
                    data = env.data,
                    traceId = env.traceId,
                    parentEventId = env.eventId
                )
                ev.markAsSent()
                ok += ev
                if (ev.createdAt > maxTs) maxTs = ev.createdAt
            } catch (ex: Exception) {
                ko += ev
                log.error("Publish failed for {}", ev.eventId, ex)
            }
        }

        /* ③ persist SENT rows in ONE master transaction */
        persistSent(ok)

        /* ④ advance cursor only if something was sent */
        if (ok.isNotEmpty()) replicaPort.write(maxTs.atZone(clock.zone).toInstant())

        /* ⑤ metrics */
        recordMetrics(ok.size, ko.size)
        meter.timer(OUTBOX_DISPATCHER_DURATION, "thread", "worker-$id")
            .record(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS)
    }

    /* master commit */
    @Transactional("transactionManager")
    fun persistSent(rows: List<OutboxEvent>) {
        if (rows.isNotEmpty()) outboxEventPort.saveAll(rows)
    }

    private fun recordMetrics(ok: Int, ko: Int) {
        if (ok > 0) meter.counter(OUTBOX_DISPATCHED_TOTAL).increment(ok.toDouble())
        if (ko > 0) meter.counter(OUTBOX_DISPATCH_FAILED_TOTAL).increment(ko.toDouble())
        meter.gauge(OUTBOX_EVENT_BACKLOG, outboxEventPort) {
            replicaPort.countByStatus("NEW").toDouble()
        }
    }
}