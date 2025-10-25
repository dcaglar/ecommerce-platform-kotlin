package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
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

    @Test
    fun `should process payment order successfully`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
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
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        every { paymentGatewayPort.charge(any()) } returns PaymentOrderStatus.SUCCESSFUL_FINAL
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { paymentOrderRepository.updateReturningIdempotent(any()) } returns paymentOrder
        every { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()
        every { meterRegistry.counter(any<String>()) } returns mockk()
        every { meterRegistry.config() } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then
        verify { paymentGatewayPort.charge(any()) }
        verify { paymentOrderRepository.findByPaymentOrderId(any()) }
        verify { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should handle PSP failure`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
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
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        every { paymentGatewayPort.charge(any()) } returns PaymentOrderStatus.FAILED_FINAL
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { paymentOrderRepository.updateReturningIdempotent(any()) } returns paymentOrder
        every { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()
        every { meterRegistry.counter(any<String>()) } returns mockk()
        every { meterRegistry.config() } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then
        verify { paymentGatewayPort.charge(any()) }
        verify { paymentOrderRepository.findByPaymentOrderId(any()) }
        verify { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should drop PSP call when payment order not found`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
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
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns emptyList()
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { meterRegistry.counter(any<String>()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then
        verify { paymentOrderRepository.findByPaymentOrderId(any()) }
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify(exactly = 0) { paymentGatewayPort.charge(any()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should drop PSP call when payment order is in terminal status`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
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
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { meterRegistry.counter(any<String>()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then
        verify { paymentOrderRepository.findByPaymentOrderId(any()) }
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify(exactly = 0) { paymentGatewayPort.charge(any()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should drop PSP call when retry count is stale`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
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
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { meterRegistry.counter(any<String>()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then
        verify { paymentOrderRepository.findByPaymentOrderId(any()) }
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify(exactly = 0) { paymentGatewayPort.charge(any()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should handle PSP timeout`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
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
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        every { paymentGatewayPort.charge(any()) } returns PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()
        every { meterRegistry.counter(any<String>()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then
        verify { paymentGatewayPort.charge(any()) }
        verify { paymentOrderRepository.findByPaymentOrderId(any()) }
        verify { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should handle PSP transient error`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
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
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        every { paymentGatewayPort.charge(any()) } returns PaymentOrderStatus.FAILED_TRANSIENT_ERROR
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()
        every { meterRegistry.counter(any<String>()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then
        verify { paymentGatewayPort.charge(any()) }
        verify { paymentOrderRepository.findByPaymentOrderId(any()) }
        verify { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should handle PSP processing status`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
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
            data = pspCallRequested,
            eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )
        
        val record = ConsumerRecord(
            "payment-psp-call",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )
        
        every { paymentGatewayPort.charge(any()) } returns PaymentOrderStatus.PENDING_STATUS_CHECK_LATER
        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { paymentOrderRepository.findByPaymentOrderId(any()) } returns listOf(paymentOrder)
        every { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()
        every { meterRegistry.counter(any<String>()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        executor.onPspRequested(record, consumer)

        // Then
        verify { paymentGatewayPort.charge(any()) }
        verify { paymentOrderRepository.findByPaymentOrderId(any()) }
        verify { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }
}