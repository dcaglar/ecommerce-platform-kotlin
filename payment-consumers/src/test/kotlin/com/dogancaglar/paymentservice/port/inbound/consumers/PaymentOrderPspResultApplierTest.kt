package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
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

class PaymentOrderPspResultApplierTest {

    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var processPspResultUseCase: ProcessPspResultUseCase
    private lateinit var clock: Clock
    private lateinit var applier: PaymentOrderPspResultApplier

    @BeforeEach
    fun setUp() {
        kafkaTxExecutor = mockk()
        processPspResultUseCase = mockk()
        clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)

        applier = PaymentOrderPspResultApplier(
            kafkaTx = kafkaTxExecutor,
            processPspResult = processPspResultUseCase
        )
    }

    @Test
    fun `should process successful PSP result`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val pspResultUpdated = PaymentOrderPspResultUpdated(
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
            lastErrorMessage = null,
            pspStatus = PaymentOrderStatus.SUCCESSFUL_FINAL.name,
            pspErrorCode = null,
            pspErrorDetail = null,
            latencyMs = 150L
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = pspResultUpdated,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-psp-result-updated",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify { processPspResultUseCase.processPspResult(pspResultUpdated, PaymentOrderStatus.SUCCESSFUL_FINAL) }
    }

    @Test
    fun `should process failed PSP result`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val pspResultUpdated = PaymentOrderPspResultUpdated(
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
            lastErrorMessage = null,
            pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR.name,
            pspErrorCode = "INSUFFICIENT_FUNDS",
            pspErrorDetail = "Account has insufficient funds",
            latencyMs = 200L
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = pspResultUpdated,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-psp-result-updated",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify { processPspResultUseCase.processPspResult(pspResultUpdated, PaymentOrderStatus.FAILED_TRANSIENT_ERROR) }
    }

    @Test
    fun `should process pending PSP result`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val pspResultUpdated = PaymentOrderPspResultUpdated(
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
            lastErrorMessage = null,
            pspStatus = PaymentOrderStatus.PENDING_STATUS_CHECK_LATER.name,
            pspErrorCode = null,
            pspErrorDetail = null,
            latencyMs = 300L
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = pspResultUpdated,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-psp-result-updated",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify { processPspResultUseCase.processPspResult(pspResultUpdated, PaymentOrderStatus.PENDING_STATUS_CHECK_LATER) }
    }

    @Test
    fun `should process timeout PSP result`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val pspResultUpdated = PaymentOrderPspResultUpdated(
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
            lastErrorMessage = null,
            pspStatus = PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT.name,
            pspErrorCode = "TIMEOUT",
            pspErrorDetail = "PSP call timed out after 1 second",
            latencyMs = 1000L
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = pspResultUpdated,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-psp-result-updated",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify { processPspResultUseCase.processPspResult(pspResultUpdated, PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT) }
    }

    @Test
    fun `should process declined PSP result`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val pspResultUpdated = PaymentOrderPspResultUpdated(
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
            lastErrorMessage = null,
            pspStatus = PaymentOrderStatus.DECLINED_FINAL.name,
            pspErrorCode = "CARD_DECLINED",
            pspErrorDetail = "Credit card was declined by issuer",
            latencyMs = 100L
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = pspResultUpdated,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-psp-result-updated",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify { processPspResultUseCase.processPspResult(pspResultUpdated, PaymentOrderStatus.DECLINED_FINAL) }
    }

    @Test
    fun `should process PSP result with error details`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val pspResultUpdated = PaymentOrderPspResultUpdated(
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
            retryCount = 1,
            retryReason = "PSP_ERROR",
            lastErrorMessage = "Previous attempt failed",
            pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR.name,
            pspErrorCode = "NETWORK_ERROR",
            pspErrorDetail = "Network connection failed during PSP call",
            latencyMs = 500L
        )
        
        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            data = pspResultUpdated,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val record = ConsumerRecord(
            "payment-order-psp-result-updated",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record, consumer)

        // Then
        verify { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify { processPspResultUseCase.processPspResult(pspResultUpdated, PaymentOrderStatus.FAILED_TRANSIENT_ERROR) }
    }

    @Test
    fun `should handle multiple PSP results with different statuses`() {
        // Given
        val paymentOrderId1 = PaymentOrderId(123L)
        val paymentOrderId2 = PaymentOrderId(124L)
        
        val pspResultUpdated1 = PaymentOrderPspResultUpdated(
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
            lastErrorMessage = null,
            pspStatus = PaymentOrderStatus.SUCCESSFUL_FINAL.name,
            pspErrorCode = null,
            pspErrorDetail = null,
            latencyMs = 150L
        )

        val pspResultUpdated2 = PaymentOrderPspResultUpdated(
            paymentOrderId = paymentOrderId2.value.toString(),
            publicPaymentOrderId = "public-124",
            paymentId = PaymentId(457L).value.toString(),
            publicPaymentId = "public-payment-124",
            sellerId = SellerId("seller-124").value,
            amountValue = 20000L,
            currency = "USD",
            status = PaymentOrderStatus.INITIATED_PENDING.name,
            createdAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            updatedAt = clock.instant().atZone(clock.zone).toLocalDateTime(),
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null,
            pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR.name,
            pspErrorCode = "INSUFFICIENT_FUNDS",
            pspErrorDetail = "Account has insufficient funds",
            latencyMs = 200L
        )
        
        val envelope1 = DomainEventEnvelopeFactory.envelopeFor(
            data = pspResultUpdated1,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId1.value.toString(),
            traceId = "trace-123",
            parentEventId = null
        )

        val envelope2 = DomainEventEnvelopeFactory.envelopeFor(
            data = pspResultUpdated2,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId2.value.toString(),
            traceId = "trace-124",
            parentEventId = null
        )

        val record1 = ConsumerRecord(
            "payment-order-psp-result-updated",
            0,
            0L,
            paymentOrderId1.value.toString(),
            envelope1
        )

        val record2 = ConsumerRecord(
            "payment-order-psp-result-updated",
            0,
            1L,
            paymentOrderId2.value.toString(),
            envelope2
        )

        every { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) } answers { 
            val lambda = thirdArg<() -> Unit>()
            lambda.invoke()
        }
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record1, consumer)
        applier.onPspResultUpdated(record2, consumer)

        // Then
        verify(exactly = 2) { kafkaTxExecutor.run(any<Map<TopicPartition, OffsetAndMetadata>>(), any(), any<() -> Unit>()) }
        verify { processPspResultUseCase.processPspResult(pspResultUpdated1, PaymentOrderStatus.SUCCESSFUL_FINAL) }
        verify { processPspResultUseCase.processPspResult(pspResultUpdated2, PaymentOrderStatus.FAILED_TRANSIENT_ERROR) }
    }
}
