package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.events.PaymentOrderFailed
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.events.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.application.metadata.EventMetadatas
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
import java.util.UUID

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
        val consumedEventId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val parentEventId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        
        val successEvent = PaymentOrderSucceeded.create(
            paymentOrderId = paymentOrderId.value.toString(),
            paymentId = PaymentId(456L).value.toString(),
            sellerId = SellerId("seller-789").value,
            amountValue = 10000L,
            currency = "EUR",
            status = "SUCCESSFUL_FINAL"
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = successEvent,
            eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord<String, EventEnvelope<PaymentOrderEvent>>(
            "payment-order-finalized",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope as EventEnvelope<PaymentOrderEvent>
        )
        
        // Mock LogContext
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
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
        
        // Verify LogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            LogContext.with<PaymentOrderEvent>(
                match { env ->
                    env is EventEnvelope<*> &&
                    env.eventId == consumedEventId &&
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
        val consumedEventId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val parentEventId = UUID.fromString("44444444-4444-4444-4444-444444444444")
        
        val failedEvent = PaymentOrderFailed.create(
            paymentOrderId = paymentOrderId.value.toString(),
            paymentId = PaymentId(789L).value.toString(),
            sellerId = SellerId("seller-101").value,
            amountValue = 5000L,
            currency = "USD",
            status = "FAILED_FINAL"
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = failedEvent,
            eventMetaData = EventMetadatas.PaymentOrderFailedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord<String, EventEnvelope<PaymentOrderEvent>>(
            "payment-order-finalized",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope as EventEnvelope<PaymentOrderEvent>
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
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
        
        // Verify LogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            LogContext.with<PaymentOrderEvent>(
                match { env ->
                    env is EventEnvelope<*> &&
                    env.eventId == consumedEventId &&
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
        val successEvent = PaymentOrderSucceeded.create(
            paymentOrderId = paymentOrderId.value.toString(),
            paymentId = PaymentId(101L).value.toString(),
            sellerId = SellerId("seller-202").value,
            amountValue = 10000L,
            currency = "EUR",
            status = "SUCCESSFUL_FINAL"
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = successEvent,
            eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-789",
            parentEventId = null
        )
        
        val record = ConsumerRecord<String, EventEnvelope<PaymentOrderEvent>>(
            "payment-order-finalized",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope as EventEnvelope<PaymentOrderEvent>
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
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

