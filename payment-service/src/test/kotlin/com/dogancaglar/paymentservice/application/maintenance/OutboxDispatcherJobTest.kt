package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.common.event.Event
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
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
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.OutboxEventRepository
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
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

class OutboxDispatcherJobTest {

    private lateinit var outboxEventRepository: OutboxEventRepository
    private lateinit var paymentOrderRepository: PaymentOrderRepository
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var objectMapper: ObjectMapper
    private lateinit var taskScheduler: ThreadPoolTaskScheduler
    private lateinit var serializationPort: SerializationPort
    private lateinit var paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
    private lateinit var idGeneratorPort: IdGeneratorPort
    private lateinit var outboxDispatcherJob: OutboxDispatcherJob

    @BeforeEach
    fun setUp() {
        outboxEventRepository = mockk(relaxed = true)
        paymentOrderRepository = mockk(relaxed = true)
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
        taskScheduler = mockk()
        
        // Mock EventLogContext
        mockkObject(EventLogContext)
        every { EventLogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
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

    @Test
    fun `should dispatch batch worker successfully`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, createEventPayload("event1")),
            createOutboxEvent(2L, createEventPayload("event2"))
        )
        
        every { outboxEventRepository.findBatchForDispatch(10, any<String>()) } returns outboxEvents
        every { eventPublisherPort.publishBatchAtomically<Event>(any(), any()) } returns true
        every { outboxEventRepository.updateAll(any()) } returns Unit

        // When
        outboxDispatcherJob.dispatchBatchWorker()

        // Then
        verify { outboxEventRepository.findBatchForDispatch(10, any<String>()) }
        verify { eventPublisherPort.publishBatchAtomically<Event>(any(), any()) }
        verify { outboxEventRepository.updateAll(any()) }
    }

    @Test
    fun `should handle empty batch gracefully`() {
        // Given
        every { outboxEventRepository.findBatchForDispatch(10, any<String>()) } returns emptyList()

        // When
        outboxDispatcherJob.dispatchBatchWorker()

        // Then
        verify { outboxEventRepository.findBatchForDispatch(10, any<String>()) }
        verify(exactly = 0) { eventPublisherPort.publishBatchAtomically<Event>(any(), any()) }
    }

    @Test
    fun `should handle publish failure and unclaim events`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, createEventPayload("event1")),
            createOutboxEvent(2L, createEventPayload("event2"))
        )
        
        every { outboxEventRepository.findBatchForDispatch(10, any<String>()) } returns outboxEvents
        every { eventPublisherPort.publishBatchAtomically<Event>(any(), any()) } returns false
        every { outboxEventRepository.unclaimSpecific(any<String>(), any<List<Long>>()) } returns 2

        // When
        outboxDispatcherJob.dispatchBatchWorker()

        // Then
        verify { outboxEventRepository.findBatchForDispatch(10, any<String>()) }
        verify { eventPublisherPort.publishBatchAtomically<Event>(any(), any()) }
        verify { outboxEventRepository.unclaimSpecific(any<String>(), listOf(1L, 2L)) }
        verify(exactly = 0) { outboxEventRepository.updateAll(any()) }
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
    fun `should handle publish exception gracefully`() {
        // Given
        val outboxEvents = listOf(
            createOutboxEvent(1L, createEventPayload("event1")),
            createOutboxEvent(2L, createEventPayload("event2"))
        )
        
        every { outboxEventRepository.findBatchForDispatch(10, any<String>()) } returns outboxEvents
        every { eventPublisherPort.publishBatchAtomically<Event>(any(), any()) } throws RuntimeException("Publish failed")
        every { outboxEventRepository.unclaimSpecific(any<String>(), any<List<Long>>()) } returns 2

        // When
        outboxDispatcherJob.dispatchBatchWorker()

        // Then
        verify { outboxEventRepository.findBatchForDispatch(10, any<String>()) }
        verify { outboxEventRepository.unclaimSpecific(any<String>(), listOf(1L, 2L)) }
    }

    private fun createOutboxEvent(oeid: Long, payload: String): OutboxEvent {
        return OutboxEvent.createNew(
            oeid = oeid,
            eventType = "payment_order_created",
            aggregateId = "test-aggregate",
            payload = payload,
        )
    }

    private fun createEventPayload(eventId: String): String {
        val now = Utc.nowLocalDateTime()
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
        val event = paymentOrderDomainEventMapper.toPaymentOrderCreated(paymentOrder)
        
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = event,
            aggregateId = event.paymentOrderId,
            traceId = "test-trace",
            parentEventId = null
        )
        
        return objectMapper.writeValueAsString(envelope)
    }
}
