package com.dogancaglar.paymentservice.port.inbound.consumers

import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.adapter.outbound.kafka.metadata.PaymentEventMetadataCatalog
import com.dogancaglar.paymentservice.config.kafka.KafkaTxExecutor
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.inbound.ProcessPspResultUseCase
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import io.mockk.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder

class PaymentOrderPspResultApplierTest {

    private lateinit var kafkaTxExecutor: KafkaTxExecutor
    private lateinit var processPspResultUseCase: ProcessPspResultUseCase
    private lateinit var paymentOrderModificationPort: PaymentOrderModificationPort
    private lateinit var applier: PaymentOrderPspResultApplier

    private lateinit var dedupe: com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort

    @BeforeEach
    fun setUp() {
        kafkaTxExecutor = mockk()
        processPspResultUseCase = mockk()
        paymentOrderModificationPort = mockk()
        dedupe = mockk(relaxed = true)

        applier = PaymentOrderPspResultApplier(
            kafkaTx = kafkaTxExecutor,
            processPspResult = processPspResultUseCase,
            dedupe = dedupe,
            paymentOrderModificationPort = paymentOrderModificationPort
        )
    }

    @Test
    fun `should process successful PSP result and call use case with exact parameters`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-123"
        val consumedEventId = "11111111-1111-1111-1111-111111111111"
        val parentEventId = "22222222-2222-2222-2222-222222222222"
        val now = Utc.nowLocalDateTime()
        val paymentId = PaymentId(456L)
        
        // PaymentOrderCaptureCommand requires CAPTURE_REQUESTED or PENDING_CAPTURE status
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-123"),
            amount = Amount.of(10000L, Currency("USD")),
            status = PaymentOrderStatus.CAPTURE_REQUESTED,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val captureCommand = PaymentOrderDomainEventMapper().toPaymentOrderCaptureCommand(paymentOrder, attempt = 0)
        val pspResultUpdated = PaymentOrderPspResultUpdated.from(
            cmd = captureCommand,
            pspStatus = PaymentOrderStatus.CAPTURED,
            latencyMs = 150L,
            now = Utc.nowInstant()
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

        every { dedupe.exists(any()) } returns false
        every { paymentOrderModificationPort.findByPaymentOrderId(paymentOrderId) } returns paymentOrder
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
                paymentOrder = paymentOrder
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
        val now = Utc.nowLocalDateTime()
        val paymentId = PaymentId(456L)
        
        // PaymentOrderCaptureCommand requires CAPTURE_REQUESTED or PENDING_CAPTURE status
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-123"),
            amount = Amount.of(10000L, Currency("USD")),
            status = PaymentOrderStatus.CAPTURE_REQUESTED,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val captureCommand = PaymentOrderDomainEventMapper().toPaymentOrderCaptureCommand(paymentOrder, attempt = 0)
        val pspResultUpdated = PaymentOrderPspResultUpdated.from(
            cmd = captureCommand,
            pspStatus = PaymentOrderStatus.PENDING_CAPTURE,
            latencyMs = 200L,
            now = Utc.nowInstant()
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

        every { dedupe.exists(any()) } returns false
        every { paymentOrderModificationPort.findByPaymentOrderId(paymentOrderId) } returns paymentOrder
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
                paymentOrder = paymentOrder
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