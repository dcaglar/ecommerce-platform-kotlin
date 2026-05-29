package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.PaymentAuthorized
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.application.events.PaymentOrderRefundReceived
import com.dogancaglar.paymentservice.domain.model.payment.OutboxEvent
import com.dogancaglar.paymentservice.ports.outbound.CentralOutboxRelayPort
import com.dogancaglar.paymentservice.ports.outbound.EdgeWatermarkPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Duration
import java.time.Instant

class OutboxRelayJobTest {

    private lateinit var centralOutboxRepository: CentralOutboxRelayPort
    private lateinit var watermarkPort: EdgeWatermarkPort
    private lateinit var kafkaPublisher: EventPublisherPort
    private lateinit var executor: ThreadPoolTaskExecutor
    private lateinit var objectMapper: ObjectMapper
    private lateinit var serializationPort: SerializationPort
    private lateinit var outboxRelayJob: OutboxRelayJob

    @BeforeEach
    fun setUp() {
        centralOutboxRepository = mockk(relaxed = true)
        watermarkPort = mockk(relaxed = true)
        kafkaPublisher = mockk(relaxed = true)
        executor = mockk(relaxed = true)
        objectMapper = mockk(relaxed = true)
        serializationPort = mockk(relaxed = true)

        // Make mock executor run tasks synchronously in tests, wrapping in try-catch to mimic background thread isolation
        every { executor.execute(any()) } answers {
            val runnable = firstArg<Runnable>()
            try {
                runnable.run()
            } catch (e: Throwable) {
                // Mimics ThreadPoolTaskExecutor background thread swallow/log behavior
            }
        }

        outboxRelayJob = OutboxRelayJob(
            centralOutboxRepository = centralOutboxRepository,
            watermarkPort = watermarkPort,
            kafkaPublisher = kafkaPublisher,
            executor = executor,
            objectMapper = objectMapper,
            serializationPort = serializationPort,
            batchSize = 100
        )
    }

    @Test
    fun `should skip poll if tSafe is null`() {
        // Given
        every { watermarkPort.computeTSafe() } returns null

        // When
        outboxRelayJob.poll()

        // Then
        verify(exactly = 0) { centralOutboxRepository.findEligible(any(), any()) }
        verify(exactly = 0) { executor.execute(any()) }
    }

    @Test
    fun `should skip poll if no eligible events`() {
        // Given
        val tSafe = Instant.ofEpochMilli(12345L)
        every { watermarkPort.computeTSafe() } returns tSafe
        every { centralOutboxRepository.findEligible(tSafe, 100) } returns emptyList()

        // When
        outboxRelayJob.poll()

        // Then
        verify(exactly = 1) { centralOutboxRepository.findEligible(tSafe, 100) }
        verify(exactly = 0) { executor.execute(any()) }
    }

    @Test
    fun `should group events by aggregateId and publish successfully`() {
        // Given
        val tSafe = Instant.ofEpochMilli(12345L)
        val now = Utc.nowLocalDateTime()
        val event1 = OutboxEvent.rehydrate(
            oeid = 1L,
            eventType = "payment_authorized",
            aggregateId = "seller-1",
            payload = "{\"paymentId\":\"1\"}",
            status = "NEW",
            createdAt = now,
            updatedAt = now
        )
        val event2 = OutboxEvent.rehydrate(
            oeid = 2L,
            eventType = "payment_order_capture_received",
            aggregateId = "seller-1",
            payload = "{\"paymentOrderId\":\"2\"}",
            status = "NEW",
            createdAt = now,
            updatedAt = now
        )
        val event3 = OutboxEvent.rehydrate(
            oeid = 3L,
            eventType = "payment_order_refund_received",
            aggregateId = "seller-2",
            payload = "{\"paymentOrderId\":\"3\"}",
            status = "NEW",
            createdAt = now,
            updatedAt = now
        )

        every { watermarkPort.computeTSafe() } returns tSafe
        every { centralOutboxRepository.findEligible(tSafe, 100) } returns listOf(event1, event2, event3)

        // Mock Jackson TypeFactory and ObjectMapper
        val mockTypeFactory = mockk<TypeFactory>()
        val mockJavaType = mockk<JavaType>()
        every { objectMapper.typeFactory } returns mockTypeFactory
        
        every { mockTypeFactory.constructParametricType(EventEnvelope::class.java, PaymentAuthorized::class.java) } returns mockJavaType
        every { mockTypeFactory.constructParametricType(EventEnvelope::class.java, PaymentOrderCaptureReceived::class.java) } returns mockJavaType
        every { mockTypeFactory.constructParametricType(EventEnvelope::class.java, PaymentOrderRefundReceived::class.java) } returns mockJavaType

        val envelope1 = mockk<EventEnvelope<PaymentAuthorized>>()
        val envelope2 = mockk<EventEnvelope<PaymentOrderCaptureReceived>>()
        val envelope3 = mockk<EventEnvelope<PaymentOrderRefundReceived>>()

        every { objectMapper.readValue<Any>(event1.payload, mockJavaType) } returns envelope1
        every { objectMapper.readValue<Any>(event2.payload, mockJavaType) } returns envelope2
        every { objectMapper.readValue<Any>(event3.payload, mockJavaType) } returns envelope3

        every { kafkaPublisher.publishBatchAtomically(listOf(envelope1), any()) } returns true
        every { kafkaPublisher.publishBatchAtomically(listOf(envelope2), any()) } returns true
        every { kafkaPublisher.publishBatchAtomically(listOf(envelope3), any()) } returns true

        // When
        outboxRelayJob.poll()

        // Then
        // Verify we grouped by aggregateId, resulting in 2 separate executor tasks:
        // One task for "seller-1" (with event1 and event2)
        // One task for "seller-2" (with event3)
        verify(exactly = 2) { executor.execute(any()) }

        // Verify Kafka publish for each event envelope
        verify(exactly = 1) { kafkaPublisher.publishBatchAtomically(listOf(envelope1), Duration.ofSeconds(10)) }
        verify(exactly = 1) { kafkaPublisher.publishBatchAtomically(listOf(envelope2), Duration.ofSeconds(10)) }
        verify(exactly = 1) { kafkaPublisher.publishBatchAtomically(listOf(envelope3), Duration.ofSeconds(10)) }

        // Verify that centralOutboxRepository.markDispatched was called for all events upon successful publish
        verify(exactly = 1) { centralOutboxRepository.markDispatched(1L) }
        verify(exactly = 1) { centralOutboxRepository.markDispatched(2L) }
        verify(exactly = 1) { centralOutboxRepository.markDispatched(3L) }
    }

    @Test
    fun `should not mark event as dispatched if Kafka publish fails`() {
        // Given
        val tSafe = Instant.ofEpochMilli(12345L)
        val now = Utc.nowLocalDateTime()
        val event = OutboxEvent.rehydrate(
            oeid = 4L,
            eventType = "payment_authorized",
            aggregateId = "seller-1",
            payload = "{\"paymentId\":\"1\"}",
            status = "NEW",
            createdAt = now,
            updatedAt = now
        )

        every { watermarkPort.computeTSafe() } returns tSafe
        every { centralOutboxRepository.findEligible(tSafe, 100) } returns listOf(event)

        val mockTypeFactory = mockk<TypeFactory>()
        val mockJavaType = mockk<JavaType>()
        every { objectMapper.typeFactory } returns mockTypeFactory
        every { mockTypeFactory.constructParametricType(EventEnvelope::class.java, PaymentAuthorized::class.java) } returns mockJavaType

        val envelope = mockk<EventEnvelope<PaymentAuthorized>>()
        every { objectMapper.readValue<Any>(event.payload, mockJavaType) } returns envelope

        // Kafka publishing fails
        every { kafkaPublisher.publishBatchAtomically(listOf(envelope), any()) } returns false

        // When
        outboxRelayJob.poll()

        // Then
        verify(exactly = 1) { kafkaPublisher.publishBatchAtomically(listOf(envelope), Duration.ofSeconds(10)) }
        verify(exactly = 0) { centralOutboxRepository.markDispatched(4L) }
    }

    @Test
    fun `should handle unexpected serialization exceptions gracefully and continue`() {
        // Given
        val tSafe = Instant.ofEpochMilli(12345L)
        val now = Utc.nowLocalDateTime()
        val event1 = OutboxEvent.rehydrate(
            oeid = 5L,
            eventType = "payment_authorized",
            aggregateId = "seller-1",
            payload = "invalid payload",
            status = "NEW",
            createdAt = now,
            updatedAt = now
        )
        val event2 = OutboxEvent.rehydrate(
            oeid = 6L,
            eventType = "payment_authorized",
            aggregateId = "seller-2",
            payload = "{\"paymentId\":\"2\"}",
            status = "NEW",
            createdAt = now,
            updatedAt = now
        )

        every { watermarkPort.computeTSafe() } returns tSafe
        every { centralOutboxRepository.findEligible(tSafe, 100) } returns listOf(event1, event2)

        val mockTypeFactory = mockk<TypeFactory>()
        val mockJavaType = mockk<JavaType>()
        every { objectMapper.typeFactory } returns mockTypeFactory
        every { mockTypeFactory.constructParametricType(EventEnvelope::class.java, PaymentAuthorized::class.java) } returns mockJavaType

        // First read throws exception, second succeeds
        every { objectMapper.readValue<Any>(event1.payload, mockJavaType) } throws RuntimeException("JSON error")
        val envelope2 = mockk<EventEnvelope<PaymentAuthorized>>()
        every { objectMapper.readValue<Any>(event2.payload, mockJavaType) } returns envelope2

        every { kafkaPublisher.publishBatchAtomically(listOf(envelope2), any()) } returns true

        // When
        outboxRelayJob.poll()

        // Then
        // Should publish event2 successfully
        verify(exactly = 1) { kafkaPublisher.publishBatchAtomically(listOf(envelope2), Duration.ofSeconds(10)) }
        // markDispatched is only called for event2
        verify(exactly = 0) { centralOutboxRepository.markDispatched(5L) }
        verify(exactly = 1) { centralOutboxRepository.markDispatched(6L) }
    }
}
