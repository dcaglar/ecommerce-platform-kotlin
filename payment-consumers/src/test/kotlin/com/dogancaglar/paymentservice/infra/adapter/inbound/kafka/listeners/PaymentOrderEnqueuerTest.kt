package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.listeners

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderCaptureReceived
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventEntityMapper
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.dogancaglar.paymentservice.domain.model.common.Amount
import com.dogancaglar.paymentservice.domain.model.common.Currency
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.payment.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.domain.exception.MissingPaymentOrderException
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import io.mockk.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PaymentOrderEnqueuerTest {

    private lateinit var eventPublisherPort: EventPublisherPort
    private lateinit var dedupe: com.dogancaglar.paymentservice.ports.outbound.EventDeduplicationPort
    private lateinit var modification: com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
    private lateinit var enqueuer: PaymentOrderEnqueuer

    @BeforeEach
    fun setUp() {
        eventPublisherPort = mockk()
        dedupe = mockk()
        modification = mockk()

        enqueuer = PaymentOrderEnqueuer(
            publisher = eventPublisherPort,
            dedupe = dedupe,
            modification = modification)
    }

    @Test
    fun `should enqueue payment order for PSP call when status is CAPTURE_RECEIVED`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val paymentId =PaymentId(456L)
        val expectedTraceId = "trace-123"
        val expectedCreatedAt = Utc.nowLocalDateTime()
        val consumedEventId = "11111111-1111-1111-1111-111111111111"
        val parentEventId = "22222222-2222-2222-2222-222222222222"
        
        val paymentOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-123"),
            amount = Amount.of(10000L, Currency("USD")),
            status = PaymentOrderStatus.CAPTURE_RECEIVED,
            retryCount = 0,
            createdAt = expectedCreatedAt,
            updatedAt = expectedCreatedAt
        )
        val paymentOrderCreated = PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureReceived(paymentOrder)
        
        // Mock the modification port to return the updated order
        val updatedOrder = paymentOrder.markCaptureRequested()
        every { dedupe.exists(any()) } returns false
        every { dedupe.markProcessed(any(), any()) } just Runs
        every { modification.updateReturningIdempotentInitialCaptureRequest(paymentOrderId.value) } returns updatedOrder
        // The mapper is a real object, so we can use it directly
        val captureCommand = PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureCommand(updatedOrder, 0)
        
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
            EventLogContext.with<PaymentOrderCaptureReceived>(
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
    fun `should skip enqueue when payment order status is not CAPTURE_RECEIVED`() {
        // Given
        val paymentOrderId = PaymentOrderId(123L)
        val expectedTraceId = "trace-456"
        val consumedEventId = "55555555-5555-5555-5555-555555555555"
        val parentEventId = "66666666-6666-6666-6666-666666666666"
        val now = Utc.nowLocalDateTime()
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
        // Create a valid PaymentOrderCaptureReceived event (requires CAPTURE_RECEIVED)
        val validOrder = PaymentOrder.rehydrate(
            paymentOrderId = paymentOrderId,
            paymentId = paymentId,
            sellerId = SellerId("seller-123"),
            amount = Amount.of(10000L, Currency("USD")),
            status = PaymentOrderStatus.CAPTURE_RECEIVED, // Must be CAPTURE_RECEIVED for PaymentOrderCaptureReceived
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
        val paymentOrderCreated = PaymentOrderDomainEventEntityMapper.toPaymentOrderCaptureReceived(validOrder)
        
        // Mock the dedupe and modification ports
        // The test simulates a scenario where markAsCaptureRequested throws MissingPaymentOrderException
        // (e.g., order was already processed or doesn't exist)
        every { dedupe.exists(any()) } returns false
        every { modification.updateReturningIdempotentInitialCaptureRequest(paymentOrderId.value) } throws MissingPaymentOrderException(paymentOrderId.value)
        
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
            EventLogContext.with<PaymentOrderCaptureReceived>(
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
