package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
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
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class PaymentOrderEnqueuerTest {

    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var clock: Clock
    private lateinit var enqueuer: PaymentOrderEnqueuer

    @BeforeEach
    fun setUp() {
        kafkaTxExecutor = mockk()
        eventPublisherPort = mockk()
        clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)

        enqueuer = PaymentOrderEnqueuer(
            kafkaTx = kafkaTxExecutor,
            publisher = eventPublisherPort,
            paymentOrderDomainEventMapper = PaymentOrderDomainEventMapper(clock)
        )
    }

    @Test
    fun `should enqueue payment order for PSP call when status is INITIATED_PENDING`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-123"
        val expectedCreatedAt = clock.instant().atZone(clock.zone).toLocalDateTime()
        val consumedEventId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val parentEventId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        
        val paymentOrderCreated = PaymentOrderCreated(
            paymentOrderId = paymentOrderId.value.toString(),
            publicPaymentOrderId = "public-123",
            paymentId = PaymentId(456L).value.toString(),
            publicPaymentId = "public-payment-123",
            sellerId = SellerId("seller-123").value,
            amountValue = 10000L,
            currency = "USD",
            status = PaymentOrderStatus.INITIATED_PENDING.name,
            createdAt = expectedCreatedAt,
            updatedAt = expectedCreatedAt,
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = paymentOrderCreated,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
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

        // Mock LogContext to allow test code execution
        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { eventPublisherPort.publishSync<PaymentOrderPspCallRequested>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        enqueuer.onCreated(record, consumer)

        // Then - verify publishSync called with exact parameters
        verify(exactly = 1) {
            eventPublisherPort.publishSync<PaymentOrderPspCallRequested>(
                preSetEventIdFromCaller = any(),
                aggregateId = paymentOrderId.value.toString(),
                eventMetaData = EventMetadatas.PaymentOrderPspCallRequestedMetadata,
                data = match { data ->
                    data is PaymentOrderPspCallRequested &&
                    data.paymentOrderId == paymentOrderId.value.toString() &&
                    data.publicPaymentOrderId == "public-123" &&
                    data.paymentId == PaymentId(456L).value.toString() &&
                    data.publicPaymentId == "public-payment-123" &&
                    data.sellerId == SellerId("seller-123").value &&
                    data.amountValue == 10000L &&
                    data.currency == "USD" &&
                    data.status == PaymentOrderStatus.INITIATED_PENDING.name &&
                    data.retryCount == 0
                },
                traceId = expectedTraceId,
                parentEventId = expectedParentEventId
            )
        }
        
        // Verify LogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            LogContext.with<PaymentOrderCreated>(
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

    @Test
    fun `should skip enqueue when payment order status is not INITIATED_PENDING`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-456"
        val consumedEventId = UUID.fromString("55555555-5555-5555-5555-555555555555")
        val parentEventId = UUID.fromString("66666666-6666-6666-6666-666666666666")
        val paymentOrderCreated = PaymentOrderCreated(
            paymentOrderId = paymentOrderId.value.toString(),
            publicPaymentOrderId = "public-123",
            paymentId = PaymentId(456L).value.toString(),
            publicPaymentId = "public-payment-123",
            sellerId = SellerId("seller-123").value,
            amountValue = 10000L,
            currency = "USD",
            status = PaymentOrderStatus.SUCCESSFUL_FINAL.name,
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = paymentOrderCreated,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
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

        mockkObject(LogContext)
        every { LogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        enqueuer.onCreated(record, consumer)

        // Then
        verify(exactly = 0) { eventPublisherPort.publishSync<PaymentOrderPspCallRequested>(any(), any(), any(), any(), any(), any(), any()) }
        
        // Verify LogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            LogContext.with<PaymentOrderCreated>(
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
}
