package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.PaymentEventMetadataCatalog
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.inbound.RequestLedgerRecordingUseCase
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import io.mockk.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.LocalDateTime
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus

class LedgerRecordingRequestDispatcherTest {

    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var requestLedgerRecordingUseCase: RequestLedgerRecordingUseCase
    private lateinit var clock: Clock
    private lateinit var dispatcher: LedgerRecordingRequestDispatcher

    @BeforeEach
    fun setUp() {
        kafkaTxExecutor = mockk()
        eventPublisherPort = mockk()
        requestLedgerRecordingUseCase = mockk()
        clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)
        
        dispatcher = LedgerRecordingRequestDispatcher(
            kafkaTx = kafkaTxExecutor,
            requestLedgerRecordingUseCase = requestLedgerRecordingUseCase
        )
    }

    // ==================== Test 1: SUCCESSFUL_FINAL Event ====================

    @Test
    fun `should dispatch LedgerRecordingCommand for SUCCESSFUL_FINAL event`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-123"
        val consumedEventId = "11111111-1111-1111-1111-111111111111"
        val parentEventId = "22222222-2222-2222-2222-222222222222"
        val now = LocalDateTime.now(clock)
        val paymentId = PaymentId(456L)
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-789"),
            amount = com.dogancaglar.paymentservice.domain.model.Amount.of(10000L, com.dogancaglar.paymentservice.domain.model.Currency("EUR")),
            status = com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus.CAPTURED,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val successEvent = PaymentOrderFinalized.from(paymentOrder, now, "SUCCESSFUL_FINAL")
        
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = successEvent,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord<String, EventEnvelope<PaymentOrderFinalized>>(
            "payment-order-finalized",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        // Mock EventLogContext
        mockkObject(EventLogContext)
        every { EventLogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { requestLedgerRecordingUseCase.requestLedgerRecording(any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        dispatcher.onPaymentOrderFinalized(record, consumer)

        // Then - verify use case called with exact event
        verify(exactly = 1) {
            requestLedgerRecordingUseCase.requestLedgerRecording(
                event = successEvent
            )
        }
        
        // Verify EventLogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            EventLogContext.with<PaymentOrderFinalized>(
                match { env ->
                    env is EventEnvelope<*> &&
                    env.eventId == successEvent.deterministicEventId() &&
                    env.aggregateId == paymentOrderId.value.toString() &&
                    env.traceId == expectedTraceId &&
                    env.parentEventId == parentEventId
                },
                any(),  // additionalContext (defaults to emptyMap)
                any()   // block lambda
            )
        }
    }

    // ==================== Test 2: FAILED_FINAL Event ====================

    @Test
    fun `should dispatch LedgerRecordingCommand for FAILED_FINAL event`() {
        // Given
        val paymentOrderId = PaymentOrderId(456L)
        val expectedTraceId = "trace-456"
        val consumedEventId = "33333333-3333-3333-3333-333333333333"
        val parentEventId = "44444444-4444-4444-4444-444444444444"
        val now = LocalDateTime.now(clock)
        val paymentId = PaymentId(789L)
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-101"),
            amount = Amount.of(5000L, Currency("USD")),
            status = PaymentOrderStatus.CAPTURE_FAILED,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val failedEvent = PaymentOrderFinalized.from(paymentOrder, now, "FAILED_FINAL")
        
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = failedEvent,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord<String, EventEnvelope<PaymentOrderFinalized>>(
            "payment-order-finalized",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        mockkObject(EventLogContext)
        every { EventLogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { requestLedgerRecordingUseCase.requestLedgerRecording(any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        dispatcher.onPaymentOrderFinalized(record, consumer)

        // Then - verify use case called with failed event
        verify(exactly = 1) {
            requestLedgerRecordingUseCase.requestLedgerRecording(
                event = failedEvent
            )
        }
        
        // Verify EventLogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            EventLogContext.with<PaymentOrderFinalized>(
                match { env ->
                    env is EventEnvelope<*> &&
                    env.eventId == failedEvent.deterministicEventId() &&
                    env.aggregateId == paymentOrderId.value.toString() &&
                    env.traceId == expectedTraceId &&
                    env.parentEventId == parentEventId
                },
                any(),  // additionalContext (defaults to emptyMap)
                any()   // block lambda
            )
        }
    }

    // ==================== Test 3: Exception Handling ====================

    @Test
    fun `should propagate exception when use case throws`() {
        // Given
        val paymentOrderId = PaymentOrderId(789L)
        val now = LocalDateTime.now(clock)
        val paymentId = PaymentId(101L)
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-202"),
            amount = Amount.of(10000L, Currency("EUR")),
            status = PaymentOrderStatus.CAPTURED,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val successEvent = PaymentOrderFinalized.from(paymentOrder, now, "SUCCESSFUL_FINAL")
        
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = successEvent,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-789",
            parentEventId = null
        )
        
        val record = ConsumerRecord<String, EventEnvelope<PaymentOrderFinalized>>(
            "payment-order-finalized",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        mockkObject(EventLogContext)
        every { EventLogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { requestLedgerRecordingUseCase.requestLedgerRecording(any()) } throws RuntimeException("Use case failed")

        // When/Then - verify exception is propagated
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        
        org.junit.jupiter.api.assertThrows<RuntimeException> {
            dispatcher.onPaymentOrderFinalized(record, consumer)
        }
        
        // Then - verify use case was called
        verify(exactly = 1) {
            requestLedgerRecordingUseCase.requestLedgerRecording(
                event = successEvent
            )
        }
    }
}

