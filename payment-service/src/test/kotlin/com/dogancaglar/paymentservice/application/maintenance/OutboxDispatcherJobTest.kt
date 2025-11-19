package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.paymentservice.adapter.outbound.persistence.OutboxOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentOrderOutboundAdapter
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class OutboxDispatcherJobTest {

    private lateinit var outboxEventRepository: OutboxOutboundAdapter
    private lateinit var paymentOrderRepository: PaymentOrderOutboundAdapter
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var objectMapper: ObjectMapper
    private lateinit var taskScheduler: ThreadPoolTaskScheduler
    private lateinit var serializationPort: SerializationPort
    private lateinit var paymentOrderDomaainEventMapper: PaymentOrderDomainEventMapper
    private lateinit var idGeneratorPort: IdGeneratorPort
    private lateinit var clock: Clock
    private lateinit var outboxDispatcherJob: OutboxDispatcherJob

    @BeforeEach
    fun setUp() {
        clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)
        outboxEventRepository = mockk<OutboxOutboundAdapter>(relaxed = true)
        paymentOrderRepository = mockk<PaymentOrderOutboundAdapter>(relaxed = true)
        eventPublisherPort = mockk(relaxed = true)
        meterRegistry = mockk(relaxed = true)
        serializationPort = mockk(relaxed = true)
        paymentOrderDomaainEventMapper = PaymentOrderDomainEventMapper(clock)
        idGeneratorPort = mockk(relaxed = true)
        objectMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        taskScheduler = mockk()
        
        outboxDispatcherJob = OutboxDispatcherJob(
            outboxEventRepository = outboxEventRepository,
            paymentOrderRepository = paymentOrderRepository,
            syncPaymentEventPublisher = eventPublisherPort,
            meterRegistry = meterRegistry,
            objectMapper = objectMapper,
            taskScheduler = taskScheduler,
            threadCount = 2,
            batchSize = 10,
            appInstanceId = "test-instance",
            clock = clock,
            backlogResyncInterval = "PT5M",
            serializationPort = serializationPort,
            paymentOrderDomainEventMapper = paymentOrderDomaainEventMapper,
            idGeneratorPort = idGeneratorPort
        )
    }

    @Test
    fun `should claim batch successfully`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, "event1"),
            createOutboxEvent(2L, "event2")
        )
        
        every { outboxEventRepository.findBatchForDispatch(10, any<String>()) } returns outboxEvents

        // When
        val result = outboxDispatcherJob.claimBatch(10, "test-worker")

        // Then
        assertEquals(2, result.size)
        assertEquals(1L, result[0].oeid)
        assertEquals(2L, result[1].oeid)
        verify { outboxEventRepository.findBatchForDispatch(10, "test-worker") }
    }

    @Test
    fun `should return empty list when no events to claim`() {
        // Given
        every { outboxEventRepository.findBatchForDispatch(10, any<String>()) } returns emptyList()

        // When
        val result = outboxDispatcherJob.claimBatch(10, "test-worker")

        // Then
        assertTrue(result.isEmpty())
        verify { outboxEventRepository.findBatchForDispatch(10, any<String>()) }
    }

    @Test
    fun `should publish batch successfully`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, createEventPayload("event1")),
            createOutboxEvent(2L, createEventPayload("event2"))
        )
        
        every { eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(any(), any<Duration>()) } returns true

        // When
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(outboxEvents)

        // Then
        assertEquals(2, succeeded.size)
        assertTrue(failed.isEmpty())
        assertTrue(keepClaimed.isEmpty())
        verify { eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(any(), any<Duration>()) }
    }

    @Test
    fun `should handle batch publish failure`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, createEventPayload("event1")),
            createOutboxEvent(2L, createEventPayload("event2"))
        )
        
        every { eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(any(), any<Duration>()) } returns false

        // When
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(outboxEvents)

        // Then
        assertTrue(succeeded.isEmpty())
        assertEquals(2, failed.size)
        assertTrue(keepClaimed.isEmpty())
        verify { eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(any(), any<Duration>()) }
    }

    @Test
    fun `should handle batch publish exception`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, createEventPayload("event1")),
            createOutboxEvent(2L, createEventPayload("event2"))
        )
        
        every { eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(any(), any<Duration>()) } throws RuntimeException("Publish failed")

        // When
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(outboxEvents)

        // Then
        assertTrue(succeeded.isEmpty())
        assertEquals(2, failed.size)
        assertTrue(keepClaimed.isEmpty())
        verify { eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(any(), any<Duration>()) }
    }

    @Test
    fun `should persist results successfully`() {
        // Given
        val succeededEvents = listOf(
            createOutboxEvent(1L, "event1").apply { markAsSent() },
            createOutboxEvent(2L, "event2").apply { markAsSent() }
        )
        
        every { outboxEventRepository.updateAll(succeededEvents) } returns Unit

        // When
        outboxDispatcherJob.persistResults(succeededEvents)

        // Then
        verify { outboxEventRepository.updateAll(succeededEvents) }
    }

    @Test
    fun `should unclaim failed events`() {
        // Given
        val failedEvents = listOf(
            createOutboxEvent(1L, "event1"),
            createOutboxEvent(2L, "event2")
        )
        
        every { outboxEventRepository.unclaimSpecific("test-worker", listOf(1L, 2L)) } returns 2

        // When
        outboxDispatcherJob.unclaimFailedNow("test-worker", failedEvents)

        // Then
        verify { outboxEventRepository.unclaimSpecific("test-worker", listOf(1L, 2L)) }
    }

    @Test
    fun `should reclaim stuck events`() {
        // Given
        every { outboxEventRepository.reclaimStuckClaims(600) } returns 5

        // When
        outboxDispatcherJob.reclaimStuck()

        // Then
        verify { outboxEventRepository.reclaimStuckClaims(600) }
    }

    @Test
    fun `should handle empty batch gracefully`() {
        // When
        val (succeeded, failed, keepClaimed) = outboxDispatcherJob.publishBatch(emptyList())

        // Then
        assertTrue(succeeded.isEmpty())
        assertTrue(failed.isEmpty())
        assertTrue(keepClaimed.isEmpty())
        verify(exactly = 0) { eventPublisherPort.publishBatchAtomically<PaymentOrderCreated>(any(), any<Duration>()) }
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
        val now = clock.instant().atZone(clock.zone).toLocalDateTime()
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = PaymentOrderId(123L),
            paymentId = PaymentId(456L),
            sellerId = SellerId("seller-789"),
            amount = Amount.of(10000L, Currency("USD")),
            status = PaymentOrderStatus.INITIATED_PENDING,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val event = paymentOrderDomaainEventMapper.toPaymentOrderCreated(paymentOrder)
        
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = event,
            aggregateId = event.paymentOrderId,
            traceId = "test-trace",
            parentEventId = null
        )
        
        return objectMapper.writeValueAsString(envelope)
    }
}
