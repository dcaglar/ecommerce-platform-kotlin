package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderCreated
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.util.PaymentOrderFactory
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
        val paymentOrderCreated = PaymentOrderCreated(
            paymentOrderId = paymentOrderId.value.toString(),
            publicPaymentOrderId = "public-123",
            paymentId = PaymentId(456L).value.toString(),
            publicPaymentId = "public-payment-123",
            sellerId = SellerId("seller-123").value,
            amountValue = 10000L,
            currency = "USD",
            status = PaymentOrderStatus.INITIATED_PENDING.name,
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = paymentOrderCreated,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-created",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        enqueuer.onCreated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should skip enqueue when payment order status is not INITIATED_PENDING`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val paymentOrderCreated = PaymentOrderCreated(
            paymentOrderId = paymentOrderId.value.toString(),
            publicPaymentOrderId = "public-123",
            paymentId = PaymentId(456L).value.toString(),
            publicPaymentId = "public-payment-123",
            sellerId = SellerId("seller-123").value,
            amountValue = 10000L,
            currency = "USD",
            status = PaymentOrderStatus.SUCCESSFUL_FINAL.name, // Non-INITIATED_PENDING status
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = paymentOrderCreated,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-created",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        enqueuer.onCreated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should skip enqueue when payment order status is PENDING_STATUS_CHECK_LATER`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val paymentOrderCreated = PaymentOrderCreated(
            paymentOrderId = paymentOrderId.value.toString(),
            publicPaymentOrderId = "public-123",
            paymentId = PaymentId(456L).value.toString(),
            publicPaymentId = "public-payment-123",
            sellerId = SellerId("seller-123").value,
            amountValue = 10000L,
            currency = "USD",
            status = PaymentOrderStatus.PENDING_STATUS_CHECK_LATER.name, // Non-INITIATED_PENDING status
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = paymentOrderCreated,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-created",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        enqueuer.onCreated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should skip enqueue when payment order status is FAILED_TRANSIENT_ERROR`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val paymentOrderCreated = PaymentOrderCreated(
            paymentOrderId = paymentOrderId.value.toString(),
            publicPaymentOrderId = "public-123",
            paymentId = PaymentId(456L).value.toString(),
            publicPaymentId = "public-payment-123",
            sellerId = SellerId("seller-123").value,
            amountValue = 10000L,
            currency = "USD",
            status = PaymentOrderStatus.FAILED_TRANSIENT_ERROR.name, // Non-INITIATED_PENDING status
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = paymentOrderCreated,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-created",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        enqueuer.onCreated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should skip enqueue when payment order status is DECLINED_FINAL`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val paymentOrderCreated = PaymentOrderCreated(
            paymentOrderId = paymentOrderId.value.toString(),
            publicPaymentOrderId = "public-123",
            paymentId = PaymentId(456L).value.toString(),
            publicPaymentId = "public-payment-123",
            sellerId = SellerId("seller-123").value,
            amountValue = 10000L,
            currency = "USD",
            status = PaymentOrderStatus.DECLINED_FINAL.name, // Non-INITIATED_PENDING status
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = paymentOrderCreated,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-created",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        enqueuer.onCreated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify(exactly = 0) { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should handle multiple payment orders with different statuses`() {
        // Given
        val paymentOrderId1 = PaymentOrderId(123L)
        val paymentOrderId2 = PaymentOrderId(124L)
        
        val paymentOrderCreated1 = PaymentOrderCreated(
            paymentOrderId = paymentOrderId1.value.toString(),
            publicPaymentOrderId = "public-123",
            paymentId = PaymentId(456L).value.toString(),
            publicPaymentId = "public-payment-123",
            sellerId = SellerId("seller-123").value,
            amountValue = 10000L,
            currency = "USD",
            status = PaymentOrderStatus.INITIATED_PENDING.name,
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )

        val paymentOrderCreated2 = PaymentOrderCreated(
            paymentOrderId = paymentOrderId2.value.toString(),
            publicPaymentOrderId = "public-124",
            paymentId = PaymentId(457L).value.toString(),
            publicPaymentId = "public-payment-124",
            sellerId = SellerId("seller-124").value,
            amountValue = 20000L,
            currency = "USD",
            status = PaymentOrderStatus.SUCCESSFUL_FINAL.name,
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null
        )
        
        val envelope1 = DomainEventEnvelopeFactory.envelopeFor(
            data = paymentOrderCreated1,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
            aggregateId = paymentOrderId1.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val envelope2 = DomainEventEnvelopeFactory.envelopeFor(
            data = paymentOrderCreated2,
            eventMetaData = EventMetadatas.PaymentOrderCreatedMetadata,
            aggregateId = paymentOrderId2.value.toString(),
            traceId = "trace-124",
            parentEventId = null
        )

        val record1 = ConsumerRecord(
            "payment-order-created",
            0,
            0L,
            paymentOrderId1.value.toString(),
            envelope1
        )

        val record2 = ConsumerRecord(
            "payment-order-created",
            0,
            1L,
            paymentOrderId2.value.toString(),
            envelope2
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        enqueuer.onCreated(record1, consumer)
        enqueuer.onCreated(record2, consumer)

        // Then
        verify(exactly = 2) { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify(exactly = 1) { eventPublisherPort.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) } // Only first one should be published
    }
}
