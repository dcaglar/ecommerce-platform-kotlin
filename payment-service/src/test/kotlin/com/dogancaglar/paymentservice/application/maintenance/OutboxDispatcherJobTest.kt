package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventMetadata
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.OutboxEvent
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class OutboxDispatcherJobTest {

    private lateinit var outboxEventPort: OutboxJobMyBatisAdapter
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var objectMapper: ObjectMapper
    private lateinit var taskScheduler: ThreadPoolTaskScheduler
    private lateinit var clock: Clock
    private lateinit var outboxDispatcherJob: OutboxDispatcherJob

    @BeforeEach
    fun setUp() {
        outboxEventPort = mockk<OutboxJobMyBatisAdapter>(relaxed = true)
        eventPublisherPort = mockk(relaxed = true)
        meterRegistry = mockk(relaxed = true)
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).registerModule(JavaTimeModule())
        taskScheduler = mockk()
        clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)
        
        outboxDispatcherJob = OutboxDispatcherJob(
            outboxEventPort = outboxEventPort,
            syncPaymentEventPublisher = eventPublisherPort,
            meterRegistry = meterRegistry,
            objectMapper = objectMapper,
            taskScheduler = taskScheduler,
            threadCount = 2,
            batchSize = 10,
            appInstanceId = "test-instance",
            clock = clock,
            backlogResyncInterval = "PT5M"
        )
    }

    @Test
    fun `should claim batch successfully`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, "event1"),
            createOutboxEvent(2L, "event2")
        )
        
        every { outboxEventPort.findBatchForDispatch(10, any<String>()) } returns outboxEvents

        // When
        val result = outboxDispatcherJob.claimBatch(10, "test-worker")

        // Then
        assertEquals(2, result.size)
        assertEquals(1L, result[0].oeid)
        assertEquals(2L, result[1].oeid)
        verify { outboxEventPort.findBatchForDispatch(10, "test-worker") }
    }

    @Test
    fun `should return empty list when no events to claim`() {
        // Given
        every { outboxEventPort.findBatchForDispatch(10, any<String>()) } returns emptyList()

        // When
        val result = outboxDispatcherJob.claimBatch(10, "test-worker")

        // Then
        assertTrue(result.isEmpty())
        verify { outboxEventPort.findBatchForDispatch(10, any<String>()) }
    }

    @Test
    fun `should publish batch successfully`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, createEventPayload("event1")),
            createOutboxEvent(2L, createEventPayload("event2"))
        )
        
        every { eventPublisherPort.publishBatchAtomically(any<List<EventEnvelope<*>>>(), any<EventMetadata<*>>(), any<Duration>()) } returns true

        // When
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(outboxEvents)

        // Then
        assertEquals(2, succeeded.size)
        assertTrue(failed.isEmpty())
        assertTrue(keepClaimed.isEmpty())
        verify { eventPublisherPort.publishBatchAtomically(any<List<EventEnvelope<*>>>(), any<EventMetadata<*>>(), any<Duration>()) }
    }

    @Test
    fun `should handle batch publish failure`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, createEventPayload("event1")),
            createOutboxEvent(2L, createEventPayload("event2"))
        )
        
        every { eventPublisherPort.publishBatchAtomically(any<List<EventEnvelope<*>>>(), any<EventMetadata<*>>(), any<Duration>()) } returns false

        // When
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(outboxEvents)

        // Then
        assertTrue(succeeded.isEmpty())
        assertEquals(2, failed.size)
        assertTrue(keepClaimed.isEmpty())
        verify { eventPublisherPort.publishBatchAtomically(any<List<EventEnvelope<*>>>(), any<EventMetadata<*>>(), any<Duration>()) }
    }

    @Test
    fun `should handle batch publish exception`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, createEventPayload("event1")),
            createOutboxEvent(2L, createEventPayload("event2"))
        )
        
        every { eventPublisherPort.publishBatchAtomically(any<List<EventEnvelope<*>>>(), any<EventMetadata<*>>(), any<Duration>()) } throws RuntimeException("Publish failed")

        // When
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(outboxEvents)

        // Then
        assertTrue(succeeded.isEmpty())
        assertEquals(2, failed.size)
        assertTrue(keepClaimed.isEmpty())
        verify { eventPublisherPort.publishBatchAtomically(any<List<EventEnvelope<*>>>(), any<EventMetadata<*>>(), any<Duration>()) }
    }

    @Test
    fun `should persist results successfully`() {
        // Given
        val succeededEvents = listOf(
            createOutboxEvent(1L, "event1").apply { markAsSent() },
            createOutboxEvent(2L, "event2").apply { markAsSent() }
        )
        
        every { outboxEventPort.updateAll(succeededEvents) } returns Unit

        // When
        outboxDispatcherJob.persistResults(succeededEvents)

        // Then
        verify { outboxEventPort.updateAll(succeededEvents) }
    }

    @Test
    fun `should unclaim failed events`() {
        // Given
        val failedEvents = listOf(
            createOutboxEvent(1L, "event1"),
            createOutboxEvent(2L, "event2")
        )
        
        every { outboxEventPort.unclaimSpecific("test-worker", listOf(1L, 2L)) } returns 2

        // When
        outboxDispatcherJob.unclaimFailedNow("test-worker", failedEvents)

        // Then
        verify { outboxEventPort.unclaimSpecific("test-worker", listOf(1L, 2L)) }
    }

    @Test
    fun `should reclaim stuck events`() {
        // Given
        every { outboxEventPort.reclaimStuckClaims(600) } returns 5

        // When
        outboxDispatcherJob.reclaimStuck()

        // Then
        verify { outboxEventPort.reclaimStuckClaims(600) }
    }

    @Test
    fun `should handle empty batch gracefully`() {
        // When
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(emptyList())

        // Then
        assertTrue(succeeded.isEmpty())
        assertTrue(failed.isEmpty())
        assertTrue(keepClaimed.isEmpty())
        verify(exactly = 0) { eventPublisherPort.publishBatchAtomically(any<List<EventEnvelope<*>>>(), any<EventMetadata<*>>(), any<Duration>()) }
    }

    private fun createOutboxEvent(oeid: Long, payload: String): OutboxEvent {
        return OutboxEvent.createNew(
            oeid = oeid,
            eventType = "payment_order_created",
            aggregateId = "test-aggregate",
            payload = payload,
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime()
        )
    }

    private fun createEventPayload(eventId: String): String {
        val event = PaymentOrderCreated(
            paymentOrderId = "po-123",
            publicPaymentOrderId = "public-po-123",
            paymentId = "p-456",
            publicPaymentId = "public-p-456",
            sellerId = "seller-789",
            amountValue = 10000L,
            currency = "USD",
            status = "CREATED",
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            retryCount = 0
        )
        
        val envelope = EventEnvelope(
            eventId = java.util.UUID.randomUUID(),
            eventType = "payment_order_created",
            aggregateId = "test-aggregate",
            traceId = "test-trace",
            parentEventId = null,
            data = event
        )
        
        return objectMapper.writeValueAsString(envelope)
    }
}
