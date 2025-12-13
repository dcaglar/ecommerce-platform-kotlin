package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.OutboxEventType
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentIntentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentIntentAuthorizedOrderLine
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.PaymentIntentStatus
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentIntentRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
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
    private lateinit var paymentOrderRepository: PaymentOrderRepository
    private lateinit var paymentIntentRepository: PaymentIntentRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var objectMapper: ObjectMapper
    private lateinit var taskScheduler: ThreadPoolTaskScheduler
    private lateinit var serializationPort: SerializationPort
    private lateinit var paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
    private lateinit var idGeneratorPort: IdGeneratorPort
    private lateinit var outboxDispatcherJob: OutboxDispatcherJob

    private val currency = Currency("EUR")
    private val amount = Amount.of(5000L, currency)
    private val buyerId = BuyerId("buyer-1")
    private val orderId = OrderId("order-1")
    private val paymentIntentId = PaymentIntentId(1L)
    private val paymentId = PaymentId(100L)
    private val sellerId1 = SellerId("seller-1")
    private val sellerId2 = SellerId("seller-2")

    @BeforeEach
    fun setUp() {
        outboxEventRepository = mockk(relaxed = true)
        paymentOrderRepository = mockk(relaxed = true)
        paymentIntentRepository = mockk(relaxed = true)
        paymentRepository = mockk(relaxed = true)
        eventPublisherPort = mockk(relaxed = true)
        meterRegistry = SimpleMeterRegistry()
        serializationPort = mockk(relaxed = true)
        paymentOrderDomainEventMapper = PaymentOrderDomainEventMapper()
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
        every { EventLogContext.getTraceId() } returns "test-trace-id"
        every { EventLogContext.getEventId() } returns "parent-event-id"
        
        outboxDispatcherJob = OutboxDispatcherJob(
            outboxEventRepository = outboxEventRepository,
            paymentOrderRepository = paymentOrderRepository,
            paymentIntentRepository = paymentIntentRepository,
            paymentRepository = paymentRepository,
            syncPaymentEventPublisher = eventPublisherPort,
            meterRegistry = meterRegistry,
            objectMapper = objectMapper,
            taskScheduler = taskScheduler,
            threadCount = 2,
            batchSize = 10,
            appInstanceId = "test-instance",
            backlogResyncInterval = "PT5M",
            serializationPort = serializationPort,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper,
            idGeneratorPort = idGeneratorPort
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(EventLogContext)
        clearAllMocks()
    }

    // ============================================================
    // Test: dispatchBatchWorker with empty batch
    // ============================================================
    @Test
    fun `dispatchBatchWorker should return early when no events to claim`() {
        val threadName = Thread.currentThread().name
        val workerId = "test-instance:$threadName"
        every { outboxEventRepository.findBatchForDispatch(10, workerId) } returns emptyList()

        outboxDispatcherJob.dispatchBatchWorker()

        verify(exactly = 1) { outboxEventRepository.findBatchForDispatch(10, workerId) }
        // With relaxed mocks, we don't need to verify non-calls
        verify(exactly = 0) { outboxEventRepository.updateAll(any()) }
    }

    // ============================================================
    // Test: handlePaymentIntentAuthorized - successful flow
    // ============================================================
    @Test
    fun `handlePaymentIntentAuthorized should create Payment and PaymentAuthorized outbox event`() {
        // Create test data
        val paymentIntent = createPaymentIntent()
        val paymentIntentAuthorizedEvent = PaymentIntentAuthorized.from(paymentIntent, Utc.nowInstant())
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentIntentAuthorizedEvent,
            aggregateId = paymentIntentAuthorizedEvent.paymentIntentId,
            parentEventId = null
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = paymentIntentId.value,
            eventType = OutboxEventType.PAYMENT_INTENT_AUTHORIZED.name,
            aggregateId = paymentIntentAuthorizedEvent.paymentIntentId,
            payload = objectMapper.writeValueAsString(envelope)
        )

        // Setup mocks
        every { paymentIntentRepository.findById(paymentIntentId) } returns paymentIntent
        every { idGeneratorPort.nextPaymentId(buyerId, orderId) } returns paymentId.value
        every { serializationPort.toJson(any<EventEnvelope<PaymentAuthorized>>()) } returns "{}"
        every { 
            eventPublisherPort.publishBatchAtomically(
                envelopes = listOf(envelope),
                timeout = Duration.ofSeconds(10)
            )
        } returns true

        // Execute
        val result = outboxDispatcherJob.handlePaymentIntentAuthorized(outboxEvent)

        // Verify
        assertTrue(result)
        verify(exactly = 1) { paymentIntentRepository.findById(paymentIntentId) }
        verify(exactly = 1) { idGeneratorPort.nextPaymentId(buyerId, orderId) }
        verify(exactly = 1) { paymentRepository.save(any<Payment>()) }
        verify(exactly = 1) { outboxEventRepository.saveAll(any<List<OutboxEvent>>()) }
        verify(exactly = 1) { 
            eventPublisherPort.publishBatchAtomically(
                envelopes = listOf(envelope),
                timeout = Duration.ofSeconds(10)
            )
        }
    }

    // ============================================================
    // Test: handlePaymentAuthorized - successful flow
    // ============================================================
    @Test
    fun `handlePaymentAuthorized should create PaymentOrders and PaymentOrderCreated outbox events`() {
        // Create test data
        val payment = createPayment()
        val paymentAuthorizedEvent = PaymentAuthorized.from(payment, Utc.nowInstant())
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentAuthorizedEvent,
            aggregateId = paymentAuthorizedEvent.paymentId,
            parentEventId = null
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = paymentId.value,
            eventType = OutboxEventType.PAYMENT_AUTHORIZED.name,
            aggregateId = paymentAuthorizedEvent.paymentId,
            payload = objectMapper.writeValueAsString(envelope)
        )

        val paymentOrderId1 = PaymentOrderId(200L)
        val paymentOrderId2 = PaymentOrderId(201L)

        // Setup mocks
        every { idGeneratorPort.nextPaymentOrderId(sellerId1) } returns paymentOrderId1.value
        every { idGeneratorPort.nextPaymentOrderId(sellerId2) } returns paymentOrderId2.value
        every { serializationPort.toJson(any<EventEnvelope<PaymentOrderCreated>>()) } returns "{}"
        every { 
            eventPublisherPort.publishBatchAtomically(
                envelopes = listOf(envelope),
                timeout = Duration.ofSeconds(10)
            )
        } returns true

        // Execute
        val result = outboxDispatcherJob.handlePaymentAuthorized(outboxEvent)

        // Verify
        assertTrue(result)
        verify(exactly = 1) { idGeneratorPort.nextPaymentOrderId(sellerId1) }
        verify(exactly = 1) { idGeneratorPort.nextPaymentOrderId(sellerId2) }
        verify(exactly = 1) { paymentOrderRepository.insertAll(any<List<PaymentOrder>>()) }
        verify(exactly = 1) { outboxEventRepository.saveAll(any<List<OutboxEvent>>()) }
        verify(exactly = 1) { 
            eventPublisherPort.publishBatchAtomically(
                envelopes = listOf(envelope),
                timeout = Duration.ofSeconds(10)
            )
        }
        
        // Verify PaymentOrders were created with correct data
        val paymentOrdersCaptor = slot<List<PaymentOrder>>()
        verify { paymentOrderRepository.insertAll(capture(paymentOrdersCaptor)) }
        val paymentOrders = paymentOrdersCaptor.captured
        assertEquals(2, paymentOrders.size)
        assertEquals(paymentId, paymentOrders[0].paymentId)
        assertEquals(sellerId1, paymentOrders[0].sellerId)
        assertEquals(paymentId, paymentOrders[1].paymentId)
        assertEquals(sellerId2, paymentOrders[1].sellerId)
    }

    // ============================================================
    // Test: handlePaymentOrderCreated - successful flow (tested via publishBatch)
    // ============================================================
    @Test
    fun `publishBatch should handle PAYMENT_ORDER_CREATED event successfully`() {
        // Create test data
        val paymentOrder = PaymentOrder.createNew(
            paymentOrderId = PaymentOrderId(300L),
            paymentId = paymentId,
            sellerId = sellerId1,
            amount = amount
        )
        val paymentOrderCreatedEvent = paymentOrderDomainEventMapper.toPaymentOrderCreated(paymentOrder)
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentOrderCreatedEvent,
            aggregateId = paymentOrderCreatedEvent.paymentOrderId,
            parentEventId = null
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = paymentOrder.paymentOrderId.value,
            eventType = OutboxEventType.PAYMENT_ORDER_CREATED.name,
            aggregateId = paymentOrderCreatedEvent.paymentOrderId,
            payload = objectMapper.writeValueAsString(envelope)
        )

        // Setup mocks
        every { 
            eventPublisherPort.publishBatchAtomically(
                envelopes = listOf(envelope),
                timeout = Duration.ofSeconds(10)
            )
        } returns true

        // Execute
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent))

        // Verify
        assertEquals(1, succeeded.size)
        assertEquals(0, failed.size)
        assertEquals(0, keepClaimed.size)
        verify(exactly = 1) { 
            eventPublisherPort.publishBatchAtomically(
                envelopes = listOf(envelope),
                timeout = Duration.ofSeconds(10)
            )
        }
    }

    // ============================================================
    // Test: publishBatch with PAYMENT_INTENT_AUTHORIZED
    // ============================================================
    @Test
    fun `publishBatch should handle PAYMENT_INTENT_AUTHORIZED event successfully`() {
        val paymentIntent = createPaymentIntent()
        val paymentIntentAuthorizedEvent = PaymentIntentAuthorized.from(paymentIntent, Utc.nowInstant())
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentIntentAuthorizedEvent,
            aggregateId = paymentIntentAuthorizedEvent.paymentIntentId,
            parentEventId = null
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = paymentIntentId.value,
            eventType = OutboxEventType.PAYMENT_INTENT_AUTHORIZED.name,
            aggregateId = paymentIntentAuthorizedEvent.paymentIntentId,
            payload = objectMapper.writeValueAsString(envelope)
        )

        every { paymentIntentRepository.findById(paymentIntentId) } returns paymentIntent
        every { idGeneratorPort.nextPaymentId(buyerId, orderId) } returns paymentId.value
        every { serializationPort.toJson(any<EventEnvelope<PaymentAuthorized>>()) } returns "{}"
        every { 
            eventPublisherPort.publishBatchAtomically<PaymentIntentAuthorized>(
                any(), any()
            )
        } returns true

        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent))

        assertEquals(1, succeeded.size)
        assertEquals(0, failed.size)
        assertEquals(0, keepClaimed.size)
        assertTrue(succeeded[0].status == OutboxEvent.Status.SENT)
    }

    // ============================================================
    // Test: publishBatch with empty list
    // ============================================================
    @Test
    fun `publishBatch should return empty triple for empty event list`() {
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(emptyList())

        assertEquals(0, succeeded.size)
        assertEquals(0, failed.size)
        assertEquals(0, keepClaimed.size)
        // With relaxed mocks, we don't need to verify non-calls
    }

    // ============================================================
    // Test: publishBatch with unknown event type
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
        // With relaxed mocks, we don't need to verify non-calls
    }

    // ============================================================
    // Test: publishBatch with publish failure
    // ============================================================
    @Test
    fun `publishBatch should mark event as failed when publish fails`() {
        val paymentIntent = createPaymentIntent()
        val paymentIntentAuthorizedEvent = PaymentIntentAuthorized.from(paymentIntent, Utc.nowInstant())
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentIntentAuthorizedEvent,
            aggregateId = paymentIntentAuthorizedEvent.paymentIntentId,
            parentEventId = null
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = paymentIntentId.value,
            eventType = OutboxEventType.PAYMENT_INTENT_AUTHORIZED.name,
            aggregateId = paymentIntentAuthorizedEvent.paymentIntentId,
            payload = objectMapper.writeValueAsString(envelope)
        )

        every { paymentIntentRepository.findById(paymentIntentId) } returns paymentIntent
        every { idGeneratorPort.nextPaymentId(buyerId, orderId) } returns paymentId.value
        every { serializationPort.toJson(any<EventEnvelope<PaymentAuthorized>>()) } returns "{}"
        every { 
            eventPublisherPort.publishBatchAtomically(
                any<List<EventEnvelope<PaymentIntentAuthorized>>>(),
                any()
            )
        } returns false

        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent))

        assertEquals(0, succeeded.size)
        assertEquals(1, failed.size)
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
    // Test: reclaimStuck
    // ============================================================
    @Test
    fun `reclaimStuck should reclaim stuck events`() {
        every { outboxEventRepository.reclaimStuckClaims(600) } returns 5

        outboxDispatcherJob.reclaimStuck()

        verify(exactly = 1) { outboxEventRepository.reclaimStuckClaims(600) }
    }

    // ============================================================
    // Test: claimBatch
    // ============================================================
    @Test
    fun `claimBatch should claim events and update backlog`() {
        val events = listOf(
            OutboxEvent.createNew(1L, "TEST", "agg-1", "{}"),
            OutboxEvent.createNew(2L, "TEST", "agg-2", "{}")
        )
        val workerId = "test-worker"

        every { outboxEventRepository.findBatchForDispatch(10, workerId) } returns events

        val result = outboxDispatcherJob.claimBatch(10, workerId)

        assertEquals(2, result.size)
        verify(exactly = 1) { outboxEventRepository.findBatchForDispatch(10, workerId) }
    }

    // ============================================================
    // Test: dispatchBatchWorker - full flow with success
    // ============================================================
    @Test
    fun `dispatchBatchWorker should process events successfully`() {
        val paymentIntent = createPaymentIntent()
        val paymentIntentAuthorizedEvent = PaymentIntentAuthorized.from(paymentIntent, Utc.nowInstant())
        val envelope = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentIntentAuthorizedEvent,
            aggregateId = paymentIntentAuthorizedEvent.paymentIntentId,
            parentEventId = null
        )
        val outboxEvent = OutboxEvent.createNew(
            oeid = paymentIntentId.value,
            eventType = OutboxEventType.PAYMENT_INTENT_AUTHORIZED.name,
            aggregateId = paymentIntentAuthorizedEvent.paymentIntentId,
            payload = objectMapper.writeValueAsString(envelope)
        )

        val workerId = "test-instance:${Thread.currentThread().name}"

        every { outboxEventRepository.findBatchForDispatch(10, workerId) } returns listOf(outboxEvent)
        every { paymentIntentRepository.findById(paymentIntentId) } returns paymentIntent
        every { idGeneratorPort.nextPaymentId(buyerId, orderId) } returns paymentId.value
        every { serializationPort.toJson(any<EventEnvelope<PaymentAuthorized>>()) } returns "{}"
        every { 
            eventPublisherPort.publishBatchAtomically<PaymentIntentAuthorized>(
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
    // Test: publishBatch with exception during processing
    // ============================================================
    @Test
    fun `publishBatch should handle exceptions and mark events as failed`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = paymentIntentId.value,
            eventType = OutboxEventType.PAYMENT_INTENT_AUTHORIZED.name,
            aggregateId = "agg-1",
            payload = "invalid-json"
        )

        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent))

        assertEquals(0, succeeded.size)
        assertEquals(1, failed.size)
        assertEquals(0, keepClaimed.size)
    }

    // ============================================================
    // Test: publishBatch with multiple events
    // ============================================================
    @Test
    fun `publishBatch should handle multiple events in batch`() {
        val paymentOrder1 = PaymentOrder.createNew(
            paymentOrderId = PaymentOrderId(300L),
            paymentId = paymentId,
            sellerId = sellerId1,
            amount = amount
        )
        val paymentOrderCreatedEvent1 = paymentOrderDomainEventMapper.toPaymentOrderCreated(paymentOrder1)
        val envelope1 = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-1",
            data = paymentOrderCreatedEvent1,
            aggregateId = paymentOrderCreatedEvent1.paymentOrderId,
            parentEventId = null
        )
        val outboxEvent1 = OutboxEvent.createNew(
            oeid = paymentOrder1.paymentOrderId.value,
            eventType = OutboxEventType.PAYMENT_ORDER_CREATED.name,
            aggregateId = paymentOrderCreatedEvent1.paymentOrderId,
            payload = objectMapper.writeValueAsString(envelope1)
        )

        val paymentOrder2 = PaymentOrder.createNew(
            paymentOrderId = PaymentOrderId(301L),
            paymentId = paymentId,
            sellerId = sellerId2,
            amount = amount
        )
        val paymentOrderCreatedEvent2 = paymentOrderDomainEventMapper.toPaymentOrderCreated(paymentOrder2)
        val envelope2 = EventEnvelopeFactory.envelopeFor(
            traceId = "trace-2",
            data = paymentOrderCreatedEvent2,
            aggregateId = paymentOrderCreatedEvent2.paymentOrderId,
            parentEventId = null
        )
        val outboxEvent2 = OutboxEvent.createNew(
            oeid = paymentOrder2.paymentOrderId.value,
            eventType = OutboxEventType.PAYMENT_ORDER_CREATED.name,
            aggregateId = paymentOrderCreatedEvent2.paymentOrderId,
            payload = objectMapper.writeValueAsString(envelope2)
        )

        every { 
            eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(
                any(),
                any()
            )
        } returns true

        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(listOf(outboxEvent1, outboxEvent2))

        assertEquals(2, succeeded.size)
        assertEquals(0, failed.size)
        assertEquals(0, keepClaimed.size)
        verify(exactly = 2) { 
            eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(
                any(),
                any()
            )
        }
    }

    // ============================================================
    // Helper methods
    // ============================================================
    private fun createPaymentIntent(): PaymentIntent {
        val paymentOrderLines = listOf(
            PaymentOrderLine(sellerId1, Amount.of(3000L, currency)),
            PaymentOrderLine(sellerId2, Amount.of(2000L, currency))
        )
        return PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = buyerId,
            orderId = orderId,
            totalAmount = amount,
            paymentOrderLines = paymentOrderLines
        ).markAuthorizedPending().markAuthorized()
    }

    private fun createPayment(): Payment {
        val paymentIntent = createPaymentIntent()
        return Payment.fromAuthorizedIntent(paymentId, paymentIntent)
    }
}
