package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.OutboxEventType
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Duration

class OutboxDispatcherJobTest {

    private lateinit var outboxEventRepository: OutboxEventRepository
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var objectMapper: ObjectMapper
    private lateinit var taskScheduler: ThreadPoolTaskScheduler
    private lateinit var idGeneratorPort: IdGeneratorPort
    private lateinit var outboxDispatcherJob: OutboxDispatcherJob

    @BeforeEach
    fun setUp() {
        outboxEventRepository = mockk(relaxed = true)
        eventPublisherPort = mockk(relaxed = true)
        meterRegistry = SimpleMeterRegistry()
        idGeneratorPort = mockk(relaxed = true)
        objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        taskScheduler = mockk(relaxed = false)
        
        // Mock EventLogContext
        mockkObject(EventLogContext)
        every { EventLogContext.with(any<EventEnvelope<*>>(), any<Map<String, String>>(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        outboxDispatcherJob = OutboxDispatcherJob(
            outboxEventRepository = outboxEventRepository,
            syncPaymentEventPublisher = eventPublisherPort,
            meterRegistry = meterRegistry,
            objectMapper = objectMapper,
            taskScheduler = taskScheduler,
            threadCount = 2,
            batchSize = 10,
            appInstanceId = "test-instance",
            backlogResyncInterval = "PT5M",
            idGeneratorPort = idGeneratorPort
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(EventLogContext)
        clearAllMocks()
    }

    // ============================================================
    // Test: claimBatch
    // ============================================================
    @Test
    fun `claimBatch should claim events and update backlog`() {
        val workerId = "test-worker"
        val events = listOf(
            OutboxEvent.createNew(1L, "payment_authorized", "agg-1", "{}"),
            OutboxEvent.createNew(2L, "payment_order_created", "agg-2", "{}")
        )

        every { outboxEventRepository.findBatchForDispatch(10, workerId) } returns events

        val result = outboxDispatcherJob.claimBatch(10, workerId)

        assertEquals(2, result.size)
        verify(exactly = 1) { outboxEventRepository.findBatchForDispatch(10, workerId) }
    }

    @Test
    fun `claimBatch should return empty list when no events available`() {
        val workerId = "test-worker"
        every { outboxEventRepository.findBatchForDispatch(10, workerId) } returns emptyList()

        val result = outboxDispatcherJob.claimBatch(10, workerId)

        assertTrue(result.isEmpty())
    }

    // ============================================================
    // Test: publishBatch - empty list
    // ============================================================
    @Test
    fun `publishBatch should return empty triple for empty event list`() {
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(emptyList())

        assertEquals(0, succeeded.size)
        assertEquals(0, failed.size)
        assertEquals(0, keepClaimed.size)
    }

    // ============================================================
    // Test: publishBatch - PaymentAuthorized success
    // ============================================================
    @Test
    fun `publishBatch should handle PaymentAuthorized event successfully`() {
        // Create domain objects
        val currency = com.dogancaglar.paymentservice.domain.model.Currency("EUR")
        val amount = com.dogancaglar.paymentservice.domain.model.Amount.of(5000L, currency)
        val paymentIntent = com.dogancaglar.paymentservice.domain.model.PaymentIntent.createNew(
            paymentIntentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId(1L),
            buyerId = com.dogancaglar.paymentservice.domain.model.vo.BuyerId("buyer-1"),
            orderId = com.dogancaglar.paymentservice.domain.model.vo.OrderId("order-1"),
            totalAmount = amount,
            paymentOrderLines = listOf(
                com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine(
                    com.dogancaglar.paymentservice.domain.model.vo.SellerId("seller-1"),
                    amount
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
            .markAuthorizedPending()
            .markAuthorized()
        
        val payment = com.dogancaglar.paymentservice.domain.model.Payment.fromAuthorizedIntent(
            paymentId = PaymentId(100L),
            intent = paymentIntent
        )
        
        val paymentAuthorizedEvent = PaymentAuthorized.from(
            payment = payment,
            timestamp = Utc.nowInstant()
        )
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentAuthorizedEvent,
            aggregateId = paymentAuthorizedEvent.paymentId,
            parentEventId = null
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = 1L,
            eventType = OutboxEventType.payment_authorized.name,
            aggregateId = paymentAuthorizedEvent.paymentId,
            payload = objectMapper.writeValueAsString(envelope)
        )

        every { 
            eventPublisherPort.publishBatchAtomically<PaymentAuthorized>(
                any(), any()
            )
        } returns true

        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent))

        assertEquals(1, succeeded.size)
        assertEquals(0, failed.size)
        assertEquals(0, keepClaimed.size)
        assertEquals(OutboxEvent.Status.SENT, succeeded[0].status)
        verify(exactly = 1) { 
            eventPublisherPort.publishBatchAtomically<PaymentAuthorized>(any(), any())
        }
    }

    // ============================================================
    // Test: publishBatch - PaymentOrderCreated success
    // ============================================================
    @Test
    fun `publishBatch should handle PaymentOrderCreated event successfully`() {
        // Create domain objects
        val currency = com.dogancaglar.paymentservice.domain.model.Currency("EUR")
        val amount = com.dogancaglar.paymentservice.domain.model.Amount.of(2500L, currency)
        val paymentOrder = com.dogancaglar.paymentservice.domain.model.PaymentOrder.createNew(
            paymentOrderId = PaymentOrderId(200L),
            paymentId = PaymentId(100L),
            sellerId = com.dogancaglar.paymentservice.domain.model.vo.SellerId("seller-1"),
            amount = amount
        )
        
        val paymentOrderCreatedEvent = PaymentOrderCreated.from(
            order = paymentOrder,
            now = Utc.nowInstant()
        )
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentOrderCreatedEvent,
            aggregateId = paymentOrderCreatedEvent.paymentOrderId,
            parentEventId = null
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = 2L,
            eventType = OutboxEventType.payment_order_created.name,
            aggregateId = paymentOrderCreatedEvent.paymentOrderId,
            payload = objectMapper.writeValueAsString(envelope)
        )

        every { 
            eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(
                any(), any()
            )
        } returns true

        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent))

        assertEquals(1, succeeded.size)
        assertEquals(0, failed.size)
        assertEquals(0, keepClaimed.size)
        assertEquals(OutboxEvent.Status.SENT, succeeded[0].status)
        verify(exactly = 1) { 
            eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(any(), any())
        }
    }

    // ============================================================
    // Test: publishBatch - publish failure
    // ============================================================
    @Test
    fun `publishBatch should mark event as failed when publish fails`() {
        // Create domain objects
        val currency = com.dogancaglar.paymentservice.domain.model.Currency("EUR")
        val amount = com.dogancaglar.paymentservice.domain.model.Amount.of(5000L, currency)
        val paymentIntent = com.dogancaglar.paymentservice.domain.model.PaymentIntent.createNew(
            paymentIntentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId(1L),
            buyerId = com.dogancaglar.paymentservice.domain.model.vo.BuyerId("buyer-1"),
            orderId = com.dogancaglar.paymentservice.domain.model.vo.OrderId("order-1"),
            totalAmount = amount,
            paymentOrderLines = listOf(
                com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine(
                    com.dogancaglar.paymentservice.domain.model.vo.SellerId("seller-1"),
                    amount
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
            .markAuthorizedPending()
            .markAuthorized()
        
        val payment = com.dogancaglar.paymentservice.domain.model.Payment.fromAuthorizedIntent(
            paymentId = PaymentId(100L),
            intent = paymentIntent
        )
        
        val paymentAuthorizedEvent = PaymentAuthorized.from(
            payment = payment,
            timestamp = Utc.nowInstant()
        )
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentAuthorizedEvent,
            aggregateId = paymentAuthorizedEvent.paymentId,
            parentEventId = null
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = 1L,
            eventType = OutboxEventType.payment_authorized.name,
            aggregateId = paymentAuthorizedEvent.paymentId,
            payload = objectMapper.writeValueAsString(envelope)
        )

        every { 
            eventPublisherPort.publishBatchAtomically<PaymentAuthorized>(
                any(), any()
            )
        } returns false

        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent))

        assertEquals(0, succeeded.size)
        assertEquals(1, failed.size)
        assertEquals(0, keepClaimed.size)
    }

    // ============================================================
    // Test: publishBatch - unknown event type
    // ============================================================
    @Test
    fun `publishBatch should mark unknown event type as failed`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = 999L,
            eventType = "UNKNOWN_EVENT_TYPE",
            aggregateId = "agg-1",
            payload = "{}"
        )

        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent))

        assertEquals(0, succeeded.size)
        assertEquals(1, failed.size)
        assertEquals(0, keepClaimed.size)
    }

    // ============================================================
    // Test: publishBatch - invalid JSON payload
    // ============================================================
    @Test
    fun `publishBatch should handle invalid JSON payload gracefully`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = 1L,
            eventType = OutboxEventType.payment_authorized.name,
            aggregateId = "agg-1",
            payload = "invalid-json"
        )

        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent))

        assertEquals(0, succeeded.size)
        assertEquals(1, failed.size)
        assertEquals(0, keepClaimed.size)
    }

    // ============================================================
    // Test: publishBatch - multiple events
    // ============================================================
    @Test
    fun `publishBatch should handle multiple events in batch`() {
        // Create domain objects for PaymentAuthorized
        val currency = com.dogancaglar.paymentservice.domain.model.Currency("EUR")
        val amount = com.dogancaglar.paymentservice.domain.model.Amount.of(5000L, currency)
        val paymentIntent = com.dogancaglar.paymentservice.domain.model.PaymentIntent.createNew(
            paymentIntentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId(1L),
            buyerId = com.dogancaglar.paymentservice.domain.model.vo.BuyerId("buyer-1"),
            orderId = com.dogancaglar.paymentservice.domain.model.vo.OrderId("order-1"),
            totalAmount = amount,
            paymentOrderLines = listOf(
                com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine(
                    com.dogancaglar.paymentservice.domain.model.vo.SellerId("seller-1"),
                    amount
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
            .markAuthorizedPending()
            .markAuthorized()
        
        val payment = com.dogancaglar.paymentservice.domain.model.Payment.fromAuthorizedIntent(
            paymentId = PaymentId(100L),
            intent = paymentIntent
        )
        
        val paymentAuthorizedEvent = PaymentAuthorized.from(
            payment = payment,
            timestamp = Utc.nowInstant()
        )
        val envelope1 = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentAuthorizedEvent,
            aggregateId = paymentAuthorizedEvent.paymentId,
            parentEventId = null
        )
        val outboxEvent1 = OutboxEvent.createNew(
            oeid = 1L,
            eventType = OutboxEventType.payment_authorized.name,
            aggregateId = paymentAuthorizedEvent.paymentId,
            payload = objectMapper.writeValueAsString(envelope1)
        )

        // Create domain objects for PaymentOrderCreated
        val paymentOrder = com.dogancaglar.paymentservice.domain.model.PaymentOrder.createNew(
            paymentOrderId = PaymentOrderId(200L),
            paymentId = PaymentId(100L),
            sellerId = com.dogancaglar.paymentservice.domain.model.vo.SellerId("seller-1"),
            amount = com.dogancaglar.paymentservice.domain.model.Amount.of(2500L, currency)
        )
        
        val paymentOrderCreatedEvent = PaymentOrderCreated.from(
            order = paymentOrder,
            now = Utc.nowInstant()
        )
        val envelope2 = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-2",
            data = paymentOrderCreatedEvent,
            aggregateId = paymentOrderCreatedEvent.paymentOrderId,
            parentEventId = null
        )
        val outboxEvent2 = OutboxEvent.createNew(
            oeid = 2L,
            eventType = OutboxEventType.payment_order_created.name,
            aggregateId = paymentOrderCreatedEvent.paymentOrderId,
            payload = objectMapper.writeValueAsString(envelope2)
        )

        every { 
            eventPublisherPort.publishBatchAtomically<PaymentAuthorized>(
                any(), any()
            )
        } returns true
        every { 
            eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(
                any(), any()
            )
        } returns true

        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent1, outboxEvent2))

        assertEquals(2, succeeded.size)
        assertEquals(0, failed.size)
        assertEquals(0, keepClaimed.size)
    }

    // ============================================================
    // Test: persistResults
    // ============================================================
    @Test
    fun `persistResults should update succeeded events`() {
        val succeededEvent = OutboxEvent.createNew(
            oeid = 1L,
            eventType = "TEST",
            aggregateId = "agg-1",
            payload = "{}"
        ).markAsSent()

        outboxDispatcherJob.persistResults(listOf(succeededEvent))

        verify(exactly = 1) { outboxEventRepository.updateAll(listOf(succeededEvent)) }
    }

    @Test
    fun `persistResults should not call updateAll for empty list`() {
        outboxDispatcherJob.persistResults(emptyList())

        verify(exactly = 0) { outboxEventRepository.updateAll(any<List<OutboxEvent>>()) }
    }

    // ============================================================
    // Test: unclaimFailedNow
    // ============================================================
    @Test
    fun `unclaimFailedNow should unclaim failed events`() {
        val failedEvent = OutboxEvent.createNew(
            oeid = 1L,
            eventType = "TEST",
            aggregateId = "agg-1",
            payload = "{}"
        )
        val workerId = "test-worker"

        every { outboxEventRepository.unclaimSpecific(workerId, listOf(1L)) } returns 1

        outboxDispatcherJob.unclaimFailedNow(workerId, listOf(failedEvent))

        verify(exactly = 1) { outboxEventRepository.unclaimSpecific(workerId, listOf(1L)) }
    }

    @Test
    fun `unclaimFailedNow should return early for empty list`() {
        outboxDispatcherJob.unclaimFailedNow("worker", emptyList())

        verify(exactly = 0) { outboxEventRepository.unclaimSpecific(any(), any()) }
    }

    // ============================================================
    // Test: dispatchBatchWorker - empty batch
    // ============================================================
    @Test
    fun `dispatchBatchWorker should return early when no events to claim`() {
        val threadName = Thread.currentThread().name
        val workerId = "test-instance:$threadName"
        every { outboxEventRepository.findBatchForDispatch(10, workerId) } returns emptyList()

        outboxDispatcherJob.dispatchBatchWorker()

        verify(exactly = 1) { outboxEventRepository.findBatchForDispatch(10, workerId) }
        verify(exactly = 0) { outboxEventRepository.updateAll(any()) }
    }

    // ============================================================
    // Test: dispatchBatchWorker - full flow with success
    // ============================================================
    @Test
    fun `dispatchBatchWorker should process events successfully`() {
        // Create domain objects
        val currency = com.dogancaglar.paymentservice.domain.model.Currency("EUR")
        val amount = com.dogancaglar.paymentservice.domain.model.Amount.of(5000L, currency)
        val paymentIntent = com.dogancaglar.paymentservice.domain.model.PaymentIntent.createNew(
            paymentIntentId = com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId(1L),
            buyerId = com.dogancaglar.paymentservice.domain.model.vo.BuyerId("buyer-1"),
            orderId = com.dogancaglar.paymentservice.domain.model.vo.OrderId("order-1"),
            totalAmount = amount,
            paymentOrderLines = listOf(
                com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine(
                    com.dogancaglar.paymentservice.domain.model.vo.SellerId("seller-1"),
                    amount
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")
            .markAuthorizedPending()
            .markAuthorized()
        
        val payment = com.dogancaglar.paymentservice.domain.model.Payment.fromAuthorizedIntent(
            paymentId = PaymentId(100L),
            intent = paymentIntent
        )
        
        val paymentAuthorizedEvent = PaymentAuthorized.from(
            payment = payment,
            timestamp = Utc.nowInstant()
        )
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentAuthorizedEvent,
            aggregateId = paymentAuthorizedEvent.paymentId,
            parentEventId = null
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = 1L,
            eventType = OutboxEventType.payment_authorized.name,
            aggregateId = paymentAuthorizedEvent.paymentId,
            payload = objectMapper.writeValueAsString(envelope)
        )

        val workerId = "test-instance:${Thread.currentThread().name}"

        every { outboxEventRepository.findBatchForDispatch(10, workerId) } returns listOf(outboxEvent)
        every { 
            eventPublisherPort.publishBatchAtomically<PaymentAuthorized>(
                any(), any()
            )
        } returns true

        outboxDispatcherJob.dispatchBatchWorker()

        verify(exactly = 1) { outboxEventRepository.findBatchForDispatch(10, workerId) }
        verify(exactly = 1) { outboxEventRepository.updateAll(any<List<OutboxEvent>>()) }
        verify(exactly = 0) { outboxEventRepository.unclaimSpecific(any(), any()) }
    }

    // ============================================================
    // Test: dispatchBatchWorker - with failures
    // ============================================================
    @Test
    fun `dispatchBatchWorker should unclaim failed events`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = 999L,
            eventType = "UNKNOWN_EVENT_TYPE",
            aggregateId = "agg-1",
            payload = "{}"
        )

        val workerId = "test-instance:${Thread.currentThread().name}"
        
        every { outboxEventRepository.findBatchForDispatch(10, workerId) } returns listOf(outboxEvent)
        every { outboxEventRepository.unclaimSpecific(workerId, listOf(999L)) } returns 1

        outboxDispatcherJob.dispatchBatchWorker()

        verify(exactly = 1) { outboxEventRepository.findBatchForDispatch(10, workerId) }
        verify(exactly = 1) { outboxEventRepository.unclaimSpecific(workerId, listOf(999L)) }
    }

    // ============================================================
    // Test: reclaimStuck
    // ============================================================
    @Test
    fun `reclaimStuck should reclaim stuck events`() {
        every { outboxEventRepository.reclaimStuckClaims(600) } returns 5

        outboxDispatcherJob.reclaimStuck()

        verify(exactly = 1) { outboxEventRepository.reclaimStuckClaims(600) }
    }
}
