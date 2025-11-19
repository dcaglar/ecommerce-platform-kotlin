package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderCreated
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.PaymentEventMetadataCatalog
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
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

class PaymentOrderEnqueuerTest {

    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var clock: Clock
    private lateinit var paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
    private lateinit var enqueuer: PaymentOrderEnqueuer

    @BeforeEach
    fun setUp() {
        kafkaTxExecutor = mockk()
        eventPublisherPort = mockk()
        clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)
        paymentOrderDomainEventMapper = mockk()

        enqueuer = PaymentOrderEnqueuer(
            kafkaTx = kafkaTxExecutor,
            publisher = eventPublisherPort,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper
        )
    }

    @Test
    fun `should enqueue payment order for PSP call when status is INITIATED_PENDING`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val paymentId =PaymentId(456L)
        val expectedTraceId = "trace-123"
        val expectedCreatedAt = clock.instant().atZone(clock.zone).toLocalDateTime()
        val consumedEventId = "11111111-1111-1111-1111-111111111111"
        val parentEventId = "22222222-2222-2222-2222-222222222222"
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-123"),
            amount = Amount.of(10000L, Currency("USD")),
            status = PaymentOrderStatus.INITIATED_PENDING,
            retryCount = 0,
            createdAt = expectedCreatedAt,
            updatedAt = expectedCreatedAt
        )
        val paymentOrderCreated = PaymentOrderDomainEventMapper(clock).toPaymentOrderCreated(paymentOrder)
        
        // Mock the mapper to return the order when fromEvent is called
        every { paymentOrderDomainEventMapper.fromEvent(paymentOrderCreated) } returns paymentOrder
        every { paymentOrderDomainEventMapper.toPaymentOrderCaptureCommand(paymentOrder, 0) } returns PaymentOrderDomainEventMapper(clock).toPaymentOrderCaptureCommand(paymentOrder, 0)
        
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = paymentOrderCreated,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )
        val expectedParentEventId = envelope.eventId  // Use the actual eventId from the envelope

        val record = ConsumerRecord(
            "payment-order-created",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        // Mock EventLogContext to allow test code execution
        mockkObject(EventLogContext)
        every { EventLogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { eventPublisherPort.publishSync<PaymentOrderCaptureCommand>(any(), any(), any(), any()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        enqueuer.onCreated(record, consumer)

        // Then - verify publishSync called with exact parameters
        verify(exactly = 1) {
            eventPublisherPort.publishSync<PaymentOrderCaptureCommand>(
                aggregateId = paymentOrderId.value.toString(),
                data = match { data ->
                    data is PaymentOrderCaptureCommand &&
                    data.paymentOrderId == paymentOrderId.value.toString() &&
                    data.publicPaymentOrderId == paymentOrderId.toPublicPaymentOrderId() &&
                    data.paymentId == paymentId.value.toString() &&
                    data.publicPaymentId == paymentId.toPublicPaymentId() &&
                    data.sellerId == SellerId("seller-123").value &&
                    data.amountValue == 10000L &&
                    data.currency == "USD" &&
                    data.attempt == 0
                },
                traceId = expectedTraceId,
                parentEventId = expectedParentEventId
            )
        }
        
        // Verify EventLogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            EventLogContext.with<PaymentOrderCreated>(
                match { env ->
                    env is EventEnvelope<*> &&
                    env.eventId == paymentOrderCreated.deterministicEventId() &&
                    env.aggregateId == paymentOrderId.value.toString() &&
                    env.traceId == expectedTraceId &&
                    env.parentEventId == parentEventId
                },
                any(),  // additionalContext (defaults to emptyMap)
                any()   // block lambda
            )
        }
    }

    @Test
    fun `should skip enqueue when payment order status is not INITIATED_PENDING`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-456"
        val consumedEventId = "55555555-5555-5555-5555-555555555555"
        val parentEventId = "66666666-6666-6666-6666-666666666666"
        val now = clock.instant().atZone(clock.zone).toLocalDateTime()
        val paymentId = PaymentId(456L)
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-123"),
            amount = Amount.of(10000L, Currency("USD")),
            status = PaymentOrderStatus.CAPTURED,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val paymentOrderCreated = PaymentOrderDomainEventMapper(clock).toPaymentOrderCreated(paymentOrder)
        
        // Mock the mapper to return the order when fromEvent is called
        every { paymentOrderDomainEventMapper.fromEvent(paymentOrderCreated) } returns paymentOrder
        
        val envelope = EventEnvelopeFactory.envelopeFor(
            data = paymentOrderCreated,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )

        val record = ConsumerRecord(
            "payment-order-created",
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
        // Mock publishSync to return a mock envelope in case it's called (shouldn't be)
        every { eventPublisherPort.publishSync<PaymentOrderCaptureCommand>(any(), any(), any(), any()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        enqueuer.onCreated(record, consumer)

        // Then
        verify(exactly = 0) { eventPublisherPort.publishSync<PaymentOrderCaptureCommand>(any(), any(), any(), any()) }
        
        // Verify EventLogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            EventLogContext.with<PaymentOrderCreated>(
                match { env ->
                    env is EventEnvelope<*> &&
                    env.eventId == paymentOrderCreated.deterministicEventId() &&
                    env.aggregateId == paymentOrderId.value.toString() &&
                    env.traceId == expectedTraceId &&
                    env.parentEventId == parentEventId
                },
                any(),  // additionalContext (defaults to emptyMap)
                any()   // block lambda
            )
        }
    }
}
