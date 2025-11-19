package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.PaymentEventMetadataCatalog
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
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
import java.time.ZoneOffset
import java.time.LocalDateTime
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder

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
        val consumedEventId = "11111111-1111-1111-1111-111111111111"
        val parentEventId = "22222222-2222-2222-2222-222222222222"
        val now = clock.instant().atZone(clock.zone).toLocalDateTime()
        val paymentId = PaymentId(456L)
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-123"),
            amount = Amount.of(10000L, Currency("USD")),
            status = PaymentOrderStatus.INITIATED_PENDING,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val captureCommand = PaymentOrderDomainEventMapper(clock).toPaymentOrderCaptureCommand(paymentOrder, attempt = 0)
        val pspResultUpdated = PaymentOrderPspResultUpdated.from(
            cmd = captureCommand,
            pspStatus = PaymentOrderStatus.CAPTURED.name,
            latencyMs = 150L,
            now = now
        )

        val envelope = EventEnvelopeFactory.envelopeFor(
            data = pspResultUpdated,
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
        every { processPspResultUseCase.processPspResult(any(), any()) } returns Unit

        // When
        val consumer = mockk<Consumer<*, *>>()
        every { consumer.groupMetadata() } returns mockk()
        applier.onPspResultUpdated(record, consumer)

        // Then - verify exact parameters
        verify(exactly = 1) {
            processPspResultUseCase.processPspResult(
                event = pspResultUpdated,
                pspStatus = PaymentOrderStatus.CAPTURED
            )
        }

        // Verify EventLogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            EventLogContext.with<PaymentOrderPspResultUpdated>(
                match { env ->
                    env is EventEnvelope<*> &&
                            env.eventId == pspResultUpdated.deterministicEventId() &&
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
        val consumedEventId = "33333333-3333-3333-3333-333333333333"
        val parentEventId = "44444444-4444-4444-4444-444444444444"
        val now = clock.instant().atZone(clock.zone).toLocalDateTime()
        val paymentId = PaymentId(456L)
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-123"),
            amount = Amount.of(10000L, Currency("USD")),
            status = PaymentOrderStatus.INITIATED_PENDING,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val captureCommand = PaymentOrderDomainEventMapper(clock).toPaymentOrderCaptureCommand(paymentOrder, attempt = 0)
        val pspResultUpdated = PaymentOrderPspResultUpdated.from(
            cmd = captureCommand,
            pspStatus = PaymentOrderStatus.PENDING_CAPTURE.name,
            latencyMs = 200L,
            now = now
        )

        val envelope = EventEnvelopeFactory.envelopeFor(
            data = pspResultUpdated,
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

        mockkObject(EventLogContext)
        every { EventLogContext.with(any<EventEnvelope<*>>(), any(), any()) } answers {
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
                pspStatus = PaymentOrderStatus.PENDING_CAPTURE
            )
        }

        // Verify EventLogContext was called with the correct envelope for tracing
        verify(exactly = 1) {
            EventLogContext.with<PaymentOrderPspResultUpdated>(
                match { env ->
                    env is EventEnvelope<*> &&
                            env.eventId == pspResultUpdated.deterministicEventId() &&
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