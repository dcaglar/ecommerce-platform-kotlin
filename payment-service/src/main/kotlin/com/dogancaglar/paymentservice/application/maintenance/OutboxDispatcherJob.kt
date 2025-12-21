package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.OutboxEventType
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentIntentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.application.util.toPublicPaymentIntentId
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCHED_TOTAL
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCHER_DURATION
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_DISPATCH_FAILED_TOTAL
import com.dogancaglar.paymentservice.metrics.MetricNames.OUTBOX_EVENT_BACKLOG
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
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
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import kotlin.collections.isNotEmpty

@Service
@DependsOn("outboxPartitionCreator")
class OutboxDispatcherJob(
    private val outboxEventRepository: OutboxEventRepository,
    private val paymentOrderRepository: PaymentOrderRepository,
    private val paymentIntentRepository: PaymentIntentRepository,
    private val paymentRepository: PaymentRepository,
    @param:Qualifier("batchPaymentEventPublisher") private val syncPaymentEventPublisher: EventPublisherPort,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper,
    @param:Qualifier("outboxJobTaskScheduler") private val taskScheduler: ThreadPoolTaskScheduler,
    @param:Value("\${outbox-dispatcher.thread-count:2}") private val threadCount: Int,
    @param:Value("\${outbox-dispatcher.batch-size:250}") private val batchSize: Int,
    @param:Value("\${app.instance-id}") private val appInstanceId: String,
    private val idGeneratorPort: IdGeneratorPort,
    @param:Value("\${outbox-backlog.resync-interval:PT5M}") private val backlogResyncInterval: String,
    private val paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
    private val serializationPort: SerializationPort
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
            val fresh = outboxEventRepository.countByStatus("NEW")
            backlog.set(fresh)
        } catch (e: Exception) {
            logger.warn("Failed to reset backlog gauge ({}): {}", reason, e.message)
        }
    }

    @Scheduled(fixedDelay = 5000)
    fun dispatchBatches() {
        repeat(threadCount) { workerIdx ->
            val delayMs = 500L * workerIdx
            taskScheduler.schedule({ dispatchBatchWorker() },
                Utc.nowInstant().plusMillis(delayMs))
        }
    }

    @Scheduled(fixedDelay = 120000)
    @Transactional(transactionManager = "outboxTxManager", timeout = 5)
    fun reclaimStuck() {
        val reclaimed = outboxEventRepository
            .reclaimStuckClaims(60 * 10)
        if (reclaimed > 0) {
            logger.warn("Reclaimer reset {} stuck outbox events to NEW", reclaimed)
            backlogAdd(reclaimed.toLong())
        }
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 2)
    fun claimBatch(batchSize: Int, workerId: String): List<OutboxEvent> {
        val claimed = outboxEventRepository
            .findBatchForDispatch(batchSize, workerId)
        if (claimed.isNotEmpty()) backlogAdd(-claimed.size.toLong())
        return claimed
    }

    fun publishBatch(events: List<OutboxEvent>)
            : Triple<List<OutboxEvent>, List<OutboxEvent>, List<OutboxEvent>> {

        if (events.isEmpty()) return Triple(emptyList(), emptyList(), emptyList())

        return try {
            val succeeded = mutableListOf<OutboxEvent>()
            val failed = mutableListOf<OutboxEvent>()

            events.forEach { evt ->
                try {

                    when (OutboxEventType.from(evt.eventType)) {
                        OutboxEventType.payment_intent_authorized -> {
                            val ok = handlePaymentIntentAuthorized(evt)
                            if (ok) succeeded += evt.markAsSent()
                            else failed += evt
                        }
                        OutboxEventType.payment_authorized -> {
                            val ok = handlePaymentAuthorized(evt)
                            if (ok) succeeded += evt.markAsSent()
                            else failed += evt
                        }

                        OutboxEventType.payment_order_created -> {
                            val ok = handlePaymentOrderCreated(evt)
                            if (ok) succeeded += evt.markAsSent()
                            else failed += evt
                        }

                        OutboxEventType.payment_order_capture_command -> {
                            /*  val ok = handlePaymentOrderCaptureCommand(evt)
                              if (ok) succeeded += evt.markAsSent()
                              else failed += evt*/
                        }

                        else -> {
                            logger.warn("❓ Unknown outbox event type=${evt.eventType}, skipping oeid=${evt.oeid}")
                            failed += evt
                        }
                    }

                } catch (ex: Exception) {
                    logger.warn("❌ Failed processing outbox event type=${evt.eventType} id=${evt.oeid}: ${ex.message}", ex)
                    failed += evt
                }
            }

            Triple(succeeded, failed, emptyList())
        } catch (t: Throwable) {
            logger.warn(
                "⚠️ Batch publish aborted; will UNCLAIM {} rows due to fatal error: {}",
                events.size, t.toString(), t
            )
            Triple(emptyList(), events.toList(), emptyList())
        }
    }

    @Transactional(transactionManager = "outboxTxManager")
    fun handlePaymentIntentAuthorized(evt: OutboxEvent):Boolean {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, PaymentIntentAuthorized::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<PaymentIntentAuthorized>
        val data = envelope.data
        EventLogContext.with(envelope) {
            // PRocess PaymentIntentAuthorized -> do nothing for now


        }
        return true
    }

    @Transactional(transactionManager = "outboxTxManager")
    fun handlePaymentAuthorized(evt: OutboxEvent):Boolean {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, PaymentAuthorized::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<PaymentAuthorized>
        var ok =false
        EventLogContext.with(envelope) {
            // Expand PaymentAuthorized -> paymentorder geneated from paymentlines + outbox<paymentordercreated>
            // create payment domain object +  paymentcreated outboxevent
            logger.info("Publising PaymentAuthorized>")


            //create outbox`<paymentordercreated> with parent being set paymentaiuthorizerd event
            // For each order, persist an OutboxEvent<PaymentOrderCreated>
            ok = syncPaymentEventPublisher.publishBatchAtomically(
                envelopes = listOf(envelope),
                timeout = java.time.Duration.ofSeconds(10)
            )

        }
        return ok
    }






    private fun handlePaymentOrderCreated(evt: OutboxEvent): Boolean {
        val envelopeType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, PaymentOrderCreated::class.java)
        val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<PaymentOrderCreated>
        logger.info("📤 Publishing PaymentOrderCreated event ${envelope.eventId}")
        val ok =syncPaymentEventPublisher.publishBatchAtomically(
            envelopes = listOf(envelope),
            timeout = java.time.Duration.ofSeconds(10)
        )

        logger.info("📤 Published PaymentOrderCreated event ${envelope.eventId}")
        return ok
    }

    /*
        private fun handlePaymentOrderCaptureCommand(evt: OutboxEvent) {
            val envelopeType = objectMapper.typeFactory
                .constructParametricType(EventEnvelope::class.java, PaymentOrderCaptureCommand::class.java)
            val envelope = objectMapper.readValue(evt.payload, envelopeType) as EventEnvelope<PaymentOrderCaptureCommand>

            // Update status (capture requested)
            paymentOrderOutboundAdapter.markCaptureRequested(envelope.data.paymentOrderId)

            // Publish to capture queue topic
            syncPaymentEventPublisher.publishBatchAtomically(
                envelopes = listOf(envelope),
                eventMetaData = PaymentEventMetadataCatalog.PaymentOrderCaptureCommandMetadata,
                timeout = java.time.Duration.ofSeconds(10)
            )

            logger.info("📤 Published PaymentOrderCaptureCommand event ${envelope.eventId}")
        }
    */



    @Transactional(transactionManager = "outboxTxManager", timeout = 5)
    fun persistResults(succeeded: List<OutboxEvent>) {
        if (succeeded.isNotEmpty()) {
            outboxEventRepository.updateAll(succeeded)
        }
    }

    @Transactional(transactionManager = "outboxTxManager", timeout = 2)
    fun unclaimFailedNow(workerId: String, failed: List<OutboxEvent>) {
        if (failed.isEmpty()) return
        val adapter = outboxEventRepository
        val n = adapter.unclaimSpecific(workerId, failed.map { it.oeid })
        if (n > 0) {
            logger.warn("Unclaimed {} failed outbox rows for worker={}", n, workerId)
            backlogAdd(n.toLong())
        }
    }

    fun dispatchBatchWorker() {
        val start = Utc.nowInstant()
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

        val durationMs = java.time.Duration.between(start, Utc.nowInstant()).toMillis()
        meterRegistry.timer(OUTBOX_DISPATCHER_DURATION, "thread", threadName)
            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if(succeeded.isNotEmpty() || failed.isNotEmpty()) {
            logger.info("Dispatched ok={} fail={} on {}", succeeded.size, failed.size, threadName)
        }
    }

    /** Adjust backlog but never let it go below zero. */
    private fun backlogAdd(delta: Long) {
        backlog.updateAndGet { curr -> maxOf(0, curr + delta) }
    }
}