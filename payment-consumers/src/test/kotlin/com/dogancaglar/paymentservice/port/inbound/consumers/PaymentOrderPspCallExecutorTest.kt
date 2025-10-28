package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderRepository
import io.micrometer.core.instrument.MeterRegistry
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

class PaymentOrderPspCallExecutorTest {

    private lateinit var paymentGatewayPort: PaymentGatewayPort
    private lateinit var paymentOrderRepository: PaymentOrderRepository
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var clock: Clock
    private lateinit var executor: PaymentOrderPspCallExecutor

    @BeforeEach
    fun setUp() {
        paymentGatewayPort = mockk()
        paymentOrderRepository = mockk()
        eventPublisherPort = mockk()
        kafkaTxExecutor = mockk()
        meterRegistry = mockk(relaxed = true)
        clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)
        
        executor = PaymentOrderPspCallExecutor(
            pspClient = paymentGatewayPort,
            meterRegistry = meterRegistry,
            kafkaTx = kafkaTxExecutor,
            publisher = eventPublisherPort,
            paymentOrderRepository = paymentOrderRepository,
            paymentOrderDomainEventMapper = PaymentOrderDomainEventMapper(clock)
        )
    }

    // ==================== Test 1: Successful PSP Call ====================

    @Test
    fun `should process payment order successfully and publish result with exact parameters`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-123"
        val consumedEventId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val parentEventId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount(10000L, "USD"))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()
        
        val pspCallRequested = PaymentOrderDomainEventMapper(clock).toPaymentOrderPspCallRequested(
            order = paymentOrder,
            attempt = 0
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )
        val expectedParentEventId = envelope.eventId  // Use the actual eventId from the envelope
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        // Mock LogContext to allow test code execution
        // Note: LogContext.with() is called with only the envelope parameter, additionalContext defaults
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { paymentGatewayPort.charge(any()) } returns PaymentOrderStatus.SUCCESSFUL_FINAL
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { paymentOrderRepository.updateReturningIdempotent(any()) } returns paymentOrder
        every { eventPublisherPort.publishSync<PaymentOrderPspResultUpdated>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()
        every { meterRegistry.counter(any()) } returns mockk()
        every { meterRegistry.config() } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then - verify PSP result event published with exact parameters
        verify(exactly = 1) {
            eventPublisherPort.publishSync<PaymentOrderPspResultUpdated>(
                preSetEventIdFromCaller = any(),
                aggregateId = paymentOrderId.value.toString(),
                eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
                data = match { result ->
                    result is PaymentOrderPspResultUpdated &&
                    result.paymentOrderId == paymentOrderId.value.toString() &&
                    result.publicPaymentOrderId == "public-123" &&
                    result.paymentId == PaymentId(456L).value.toString() &&
                    result.publicPaymentId == "public-payment-123" &&
                    result.sellerId == SellerId("seller-123").value &&
                    result.amountValue == 10000L &&
                    result.currency == "USD" &&
                    result.status == PaymentOrderStatus.INITIATED_PENDING.name &&
                    result.pspStatus == PaymentOrderStatus.SUCCESSFUL_FINAL.name &&
                    result.retryCount == 0 &&
                    result.latencyMs != null && result.latencyMs!! >= 0
                },
                traceId = expectedTraceId,
                parentEventId = expectedParentEventId,
                timeoutSeconds = any()
            )
        }
        
        // Verify LogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            LogContext.with<PaymentOrderPspCallRequested>(
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

    // ==================== Test 2: Failed PSP Call ====================

    @Test
    fun `should handle PSP failure and publish result with exact parameters`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-456"
        val consumedEventId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val parentEventId = UUID.fromString("44444444-4444-4444-4444-444444444444")
        
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount(5000L, "EUR"))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()
        
        val pspCallRequested = PaymentOrderDomainEventMapper(clock).toPaymentOrderPspCallRequested(
            order = paymentOrder,
            attempt = 0
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )
        val expectedParentEventId = envelope.eventId  // Use the actual eventId from the envelope
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { paymentGatewayPort.charge(any()) } returns PaymentOrderStatus.DECLINED_FINAL
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { paymentOrderRepository.updateReturningIdempotent(any()) } returns paymentOrder
        every { eventPublisherPort.publishSync<PaymentOrderPspResultUpdated>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()
        every { meterRegistry.counter(any()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then - verify PSP failure event published
        verify(exactly = 1) {
            eventPublisherPort.publishSync<PaymentOrderPspResultUpdated>(
                preSetEventIdFromCaller = any(),
                aggregateId = paymentOrderId.value.toString(),
                eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
                data = match { result ->
                    result is PaymentOrderPspResultUpdated &&
                    result.pspStatus == PaymentOrderStatus.DECLINED_FINAL.name &&
                    result.latencyMs != null && result.latencyMs!! >= 0
                },
                traceId = expectedTraceId,
                parentEventId = expectedParentEventId,
                timeoutSeconds = any()
            )
        }
    }

    // ==================== Test 3: Drop When Payment Order Not Found ====================

    @Test
    fun `should drop PSP call when payment order not found`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val consumedEventId = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val parentEventId = UUID.fromString("66666666-6666-6666-6666-666666666666")
        val pspCallRequested = PaymentOrderDomainEventMapper(clock).toPaymentOrderPspCallRequested(
            order = PaymentOrder.builder()
                .paymentOrderId(paymentOrderId)
                .publicPaymentOrderId("public-123")
                .paymentId(PaymentId(456L))
                .publicPaymentId("public-payment-123")
                .sellerId(SellerId("seller-123"))
                .amount(Amount(10000L, "USD"))
                .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
                .buildNew(),
            attempt = 0
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns emptyList()
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { meterRegistry.counter(any()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then - verify NO PSP call and NO event published
        verify(exactly = 0) { paymentGatewayPort.charge(any()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<PaymentOrderPspResultUpdated>(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ==================== Test 4: Drop When Payment Order in Terminal Status ====================

    @Test
    fun `should drop PSP call when payment order is in terminal status`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val consumedEventId = UUID.fromString("77777777-7777-7777-7777-777777777777")
        val parentEventId = UUID.fromString("88888888-8888-8888-8888-888888888888")
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount(10000L, "USD"))
            .status(PaymentOrderStatus.SUCCESSFUL_FINAL) // Terminal status
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .updatedAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()
        
        val pspCallRequested = PaymentOrderDomainEventMapper(clock).toPaymentOrderPspCallRequested(
            order = paymentOrder,
            attempt = 0
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { meterRegistry.counter(any()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then - verify NO PSP call and NO event published
        verify(exactly = 0) { paymentGatewayPort.charge(any()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<PaymentOrderPspResultUpdated>(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ==================== Test 5: Drop When Retry Count is Stale ====================

    @Test
    fun `should drop PSP call when retry count is stale`() {
        // Given - payment order has higher retry count (2) than event (1)
        val paymentOrderId = PaymentOrderId(123L)
        val consumedEventId = UUID.fromString("99999999-9999-9999-9999-999999999999")
        val parentEventId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount(10000L, "USD"))
            .status(PaymentOrderStatus.PENDING_STATUS_CHECK_LATER)
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .updatedAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .retryCount(2) // Higher retry count in DB
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()
        
        val pspCallRequested = PaymentOrderDomainEventMapper(clock).toPaymentOrderPspCallRequested(
            order = paymentOrder,
            attempt = 1 // Lower attempt in event
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { meterRegistry.counter(any()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then - verify NO PSP call and NO event published
        verify(exactly = 0) { paymentGatewayPort.charge(any()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<PaymentOrderPspResultUpdated>(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ==================== Test 6: PSP Timeout ====================

    @Test
    fun `should handle PSP timeout and publish result`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val consumedEventId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val parentEventId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount(10000L, "USD"))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()
        
        val pspCallRequested = PaymentOrderDomainEventMapper(clock).toPaymentOrderPspCallRequested(
            order = paymentOrder,
            attempt = 0
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { paymentGatewayPort.charge(any()) } returns PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { paymentOrderRepository.updateReturningIdempotent(any()) } returns paymentOrder
        every { eventPublisherPort.publishSync<PaymentOrderPspResultUpdated>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()
        every { meterRegistry.counter(any()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then - verify timeout status was published
        verify(exactly = 1) {
            eventPublisherPort.publishSync<PaymentOrderPspResultUpdated>(
                preSetEventIdFromCaller = any(),
                aggregateId = paymentOrderId.value.toString(),
                eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
                data = match { result ->
                    result is PaymentOrderPspResultUpdated &&
                    result.pspStatus == PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT.name
                },
                traceId = "trace-123",
                parentEventId = any(),
                timeoutSeconds = any()
            )
        }
    }

    // ==================== Test 7: Exception During PSP Call ====================

    @Test
    fun `should propagate exception when PSP call throws non-retryable exception`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val consumedEventId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
        val parentEventId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")
        
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(paymentOrderId)
            .publicPaymentOrderId("public-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("public-payment-123")
            .sellerId(SellerId("seller-123"))
            .amount(Amount(10000L, "USD"))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .buildNew()
        
        val pspCallRequested = PaymentOrderDomainEventMapper(clock).toPaymentOrderPspCallRequested(
            order = paymentOrder,
            attempt = 0
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = parentEventId
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        
        val exception = RuntimeException("PSP connection failed")
        every { paymentGatewayPort.charge(any()) } throws exception
        
        // When/Then
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        
        org.junit.jupiter.api.assertThrows<RuntimeException> {
            executor.onPspRequested(record, consumer)
        }
        
        // Verify exception propagated
        verify(exactly = 1) { paymentGatewayPort.charge(any()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<PaymentOrderPspResultUpdated>(any(), any(), any(), any(), any(), any(), any()) }
    }
}
