package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.payment.Payment
import com.dogancaglar.paymentservice.domain.model.payment.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxEdgePort
import com.dogancaglar.paymentservice.ports.outbound.LocalOutboxEdgePort
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

class LocalOutboxForwarderJobTest {

    private lateinit var outboxEventRepository: LocalOutboxEdgePort
    private lateinit var centralOutboxRepository: CentralOutboxEdgePort
    private lateinit var taskScheduler: ThreadPoolTaskScheduler
    private lateinit var meterRegistry: io.micrometer.core.instrument.MeterRegistry
    private lateinit var localOutboxForwarderJob: LocalOutboxForwarderJob

    @BeforeEach
    fun setUp() {
        outboxEventRepository = mockk(relaxed = true)
        centralOutboxRepository = mockk(relaxed = true)
        taskScheduler = mockk(relaxed = false)

        meterRegistry = io.micrometer.core.instrument.simple.SimpleMeterRegistry()

        localOutboxForwarderJob = LocalOutboxForwarderJob(
            outboxEventRepository = outboxEventRepository,
            centralOutboxRepository = centralOutboxRepository,
            taskScheduler = taskScheduler,
            threadCount = 2,
            batchSize = 10,
            appInstanceId = "test-edge-node",
            meterRegistry = meterRegistry
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ============================================================
    // Test: claimBatch
    // ============================================================
    @Test
    fun `claimBatch should claim events`() {
        val workerId = "test-worker"
        val events = listOf(
            OutboxEvent.createNew(1L, "payment_authorized", "agg-1", "{}"),
            OutboxEvent.createNew(2L, "payment_order_capture_received", "agg-2", "{}")
        )

        every { outboxEventRepository.findEligible(10, workerId) } returns events

        val result = localOutboxForwarderJob.claimBatch(10, workerId)

        assertEquals(2, result.size)
        verify(exactly = 1) { outboxEventRepository.findEligible(10, workerId) }
    }

    @Test
    fun `claimBatch should return empty list when no events available`() {
        val workerId = "test-worker"
        every { outboxEventRepository.findEligible(10, workerId) } returns emptyList()

        val result = localOutboxForwarderJob.claimBatch(10, workerId)

        assertTrue(result.isEmpty())
    }

    // ============================================================
    // Test: forwardBatch - empty list
    // ============================================================
    @Test
    fun `forwardBatch should return true for empty event list`() {
        val result = localOutboxForwarderJob.forwardBatch(emptyList())
        assertTrue(result)
    }

    // ============================================================
    // Test: forwardBatch - success and watermark progress
    // ============================================================
    @Test
    fun `forwardBatch should forward PaymentAuthorized events and update watermark`() {
        // Create domain objects for PaymentAuthorized
        val currency = Currency("EUR")
        val amount = Amount.of(5000L, currency)
        val paymentIntent = PaymentIntent.createNew(
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

        val payment = Payment.fromAuthorizedIntent(
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
            oeid = 101L,
            eventType = "payment_authorized",
            aggregateId = paymentAuthorizedEvent.paymentId,
            payload = "{}"
        )

        // Mock insert and watermark updates
        every { centralOutboxRepository.insertBatch(any(), any()) } just Runs
        every { centralOutboxRepository.updateWatermark(any(), any()) } just Runs

        val result = localOutboxForwarderJob.forwardBatch(listOf(outboxEvent))

        assertTrue(result)

        verify(exactly = 1) {
            centralOutboxRepository.insertBatch("test-edge-node", withArg { entries ->
                assertEquals(1, entries.size)
                assertEquals(101L, entries[0].oeid)
                assertEquals("payment_authorized", entries[0].eventType)
                assertEquals("100", entries[0].aggregateId)
                assertEquals(outboxEvent.payload, entries[0].payload)
            })
        }
        verify(exactly = 1) {
            centralOutboxRepository.updateWatermark("test-edge-node", Utc.toInstant(outboxEvent.createdAt))
        }
    }

    // ============================================================
    // Test: forwardBatch - failures
    // ============================================================
    @Test
    fun `forwardBatch should return false when insert fails`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = 103L,
            eventType = "test_event",
            aggregateId = "agg-1",
            payload = "{\"eventId\":\"evt-abc\",\"data\":{}}"
        )

        every { centralOutboxRepository.insertBatch(any(), any()) } throws RuntimeException("Central Database Connection Interrupted")

        val result = localOutboxForwarderJob.forwardBatch(listOf(outboxEvent))

        assertFalse(result)
        verify(exactly = 0) { centralOutboxRepository.updateWatermark(any(), any()) }
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

        localOutboxForwarderJob.persistResults(listOf(succeededEvent))

        verify(exactly = 1) { outboxEventRepository.markDispatched(listOf(succeededEvent)) }
    }

    @Test
    fun `persistResults should not call updateAll for empty list`() {
        localOutboxForwarderJob.persistResults(emptyList())

        verify(exactly = 0) { outboxEventRepository.markDispatched(any<List<OutboxEvent>>()) }
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

        every { outboxEventRepository.unclaimFailed(workerId, listOf(1L)) } returns 1

        localOutboxForwarderJob.unclaimFailedNow(workerId, listOf(failedEvent))

        verify(exactly = 1) { outboxEventRepository.unclaimFailed(workerId, listOf(1L)) }
    }

    @Test
    fun `unclaimFailedNow should return early for empty list`() {
        localOutboxForwarderJob.unclaimFailedNow("worker", emptyList())

        verify(exactly = 0) { outboxEventRepository.unclaimFailed(any(), any()) }
    }

    // ============================================================
    // Test: dispatchBatchWorker - empty batch
    // ============================================================
    @Test
    fun `dispatchBatchWorker should return early when no events to claim`() {
        val threadName = Thread.currentThread().name
        val workerId = "test-edge-node:$threadName"
        every { outboxEventRepository.findEligible(10, workerId) } returns emptyList()

        localOutboxForwarderJob.dispatchBatchWorker()

        verify(exactly = 1) { outboxEventRepository.findEligible(10, workerId) }
        verify(exactly = 0) { centralOutboxRepository.insertBatch(any(), any()) }
        verify(exactly = 0) { outboxEventRepository.markDispatched(any()) }
    }

    // ============================================================
    // Test: dispatchBatchWorker - full success flow
    // ============================================================
    @Test
    fun `dispatchBatchWorker should claim, forward, and persist successfully`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = 105L,
            eventType = "test_event",
            aggregateId = "agg-1",
            payload = "{\"eventId\":\"evt-555\",\"data\":{}}"
        )

        val workerId = "test-edge-node:${Thread.currentThread().name}"

        every { outboxEventRepository.findEligible(10, workerId) } returns listOf(outboxEvent)
        every { centralOutboxRepository.insertBatch(any(), any()) } just Runs
        every { centralOutboxRepository.updateWatermark(any(), any()) } just Runs
        every { outboxEventRepository.markDispatched(any()) } just Runs

        localOutboxForwarderJob.dispatchBatchWorker()

        verify(exactly = 1) { outboxEventRepository.findEligible(10, workerId) }
        verify(exactly = 1) { centralOutboxRepository.insertBatch(any(), any()) }
        verify(exactly = 1) { centralOutboxRepository.updateWatermark(any(), any()) }
        verify(exactly = 1) { outboxEventRepository.markDispatched(any()) }
        verify(exactly = 0) { outboxEventRepository.unclaimFailed(any(), any()) }
    }

    @Test
    fun `dispatchBatchWorker should unclaim failed events on forward failure`() {
        val outboxEvent = OutboxEvent.createNew(
            oeid = 999L,
            eventType = "test_event",
            aggregateId = "agg-1",
            payload = "{\"eventId\":\"evt-555\",\"data\":{}}"
        )

        val workerId = "test-edge-node:${Thread.currentThread().name}"

        every { outboxEventRepository.findEligible(10, workerId) } returns listOf(outboxEvent)
        every { centralOutboxRepository.insertBatch(any(), any()) } throws RuntimeException("Connection Failed")
        every { outboxEventRepository.unclaimFailed(workerId, listOf(999L)) } returns 1

        localOutboxForwarderJob.dispatchBatchWorker()

        verify(exactly = 1) { outboxEventRepository.findEligible(10, workerId) }
        verify(exactly = 1) { outboxEventRepository.unclaimFailed(workerId, listOf(999L)) }
        verify(exactly = 0) { outboxEventRepository.markDispatched(any()) }
    }

    // ============================================================
    // Test: reclaimStuck
    // ============================================================
    @Test
    fun `reclaimStuck should reclaim stuck events`() {
        every { outboxEventRepository.reclaimStuck(600) } returns 5

        localOutboxForwarderJob.reclaimStuck()

        verify(exactly = 1) { outboxEventRepository.reclaimStuck(600) }
    }
}
