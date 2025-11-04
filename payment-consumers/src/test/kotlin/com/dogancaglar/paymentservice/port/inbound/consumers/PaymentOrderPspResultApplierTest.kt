package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.DomainEventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
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
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

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
    fun `should process successful PSP result and call use case with exact parameters`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-123"
        val consumedEventId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val parentEventId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val pspResultUpdated = PaymentOrderPspResultUpdated.create(
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
            preSetEventId = consumedEventId,
            data = pspResultUpdated,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )

        val record = ConsumerRecord(
            "payment-order-psp-result-updated",
            0,
            0L,
            paymentOrderId.value.toString(),
            envelope
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
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record, consumer)

        // Then - verify exact parameters
        verify(exactly = 1) {
            processPspResultUseCase.processPspResult(
                event = pspResultUpdated,
                pspStatus = PaymentOrderStatus.SUCCESSFUL_FINAL
            )
        }
        
        // Verify LogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            LogContext.with<PaymentOrderPspResultUpdated>(
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
    fun `should process failed PSP result and call use case with exact status`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-456"
        val consumedEventId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val parentEventId = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val pspResultUpdated = PaymentOrderPspResultUpdated.create(
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
            preSetEventId = consumedEventId,
            data = pspResultUpdated,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )

        val record = ConsumerRecord(
            "payment-order-psp-result-updated",
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
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record, consumer)

        // Then
        verify(exactly = 1) {
            processPspResultUseCase.processPspResult(
                event = pspResultUpdated,
                pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR
            )
        }
        
        // Verify LogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            LogContext.with<PaymentOrderPspResultUpdated>(
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
    fun `should handle different PSP statuses correctly`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-789"
        val consumedEventId = UUID.fromString("77777777-7777-7777-7777-777777777777")
        val parentEventId = UUID.fromString("88888888-8888-8888-8888-888888888888")
        val localDateTime = clock.instant().atZone(clock.zone).toLocalDateTime()

        val pendingResult = PaymentOrderPspResultUpdated.create(
            paymentOrderId = paymentOrderId.value.toString(),
            publicPaymentOrderId = "public-123",
            paymentId = PaymentId(456L).value.toString(),
            publicPaymentId = "public-payment-123",
            sellerId = SellerId("seller-123").value,
            amountValue = 10000L,
            currency = "USD",
            status = PaymentOrderStatus.INITIATED_PENDING.name,
            createdAt = localDateTime,
            updatedAt = localDateTime,
            retryCount = 0,
            retryReason = null,
            lastErrorMessage = null,
            pspStatus = PaymentOrderStatus.PENDING_STATUS_CHECK_LATER.name,
            pspErrorCode = null,
            pspErrorDetail = null,
            latencyMs = 300L
        )

        val envelope = DomainEventEnvelopeFactory.envelopeFor(
            preSetEventId = consumedEventId,
            data = pendingResult,
            eventMetaData = EventMetadatas.PaymentOrderPspResultUpdatedMetadata,
            aggregateId = paymentOrderId.value.toString(),
            traceId = expectedTraceId,
            parentEventId = parentEventId
        )

        val record = ConsumerRecord(
            "payment-order-psp-result-updated",
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
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record, consumer)

        // Then
        verify(exactly = 1) {
            processPspResultUseCase.processPspResult(
                event = pendingResult,
                pspStatus = PaymentOrderStatus.PENDING_STATUS_CHECK_LATER
            )
        }
        
        // Verify LogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            LogContext.with<PaymentOrderPspResultUpdated>(
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