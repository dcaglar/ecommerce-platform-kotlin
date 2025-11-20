package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderFinalized
import com.dogancaglar.paymentservice.application.events.PaymentOrderPspResultUpdated
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ProcessPaymentServiceTest {

    private lateinit var eventPublisher: EventPublisherPort
    private lateinit var retryQueuePort: RetryQueuePort<PaymentOrderCaptureCommand>
    private lateinit var paymentOrderModificationPort: PaymentOrderModificationPort
    private lateinit var paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
    private lateinit var clock: Clock
    private lateinit var service: ProcessPaymentService

    @BeforeEach
    fun setUp() {
        eventPublisher = mockk()
        retryQueuePort = mockk(relaxed = true)
        paymentOrderModificationPort = mockk()
        paymentOrderDomainEventMapper = mockk()
        clock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneId.of("UTC"))

        mockkObject(EventLogContext)
        every { EventLogContext.getEventId() } returns null
        every { EventLogContext.getTraceId() } returns "test-trace-id"

        service = ProcessPaymentService(
            eventPublisher = eventPublisher,
            retryQueuePort = retryQueuePort,
            paymentOrderModificationPort = paymentOrderModificationPort,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper,
            clock = clock
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkObject(EventLogContext)
    }

    @Test
    fun `processPspResult publishes success for captured status`() {
        val event = samplePspResultEvent(PaymentOrderStatus.CAPTURED)
        val order = sampleOrder(status = PaymentOrderStatus.CAPTURE_REQUESTED)
        val persisted = sampleOrder(status = PaymentOrderStatus.CAPTURED)
        val now = LocalDateTime.now(clock)
        val succeededEvent = PaymentOrderFinalized.from(
            order = persisted,
            now = now,
            status = PaymentOrderStatus.CAPTURED
        )
        every { paymentOrderModificationPort.markAsCaptured(order) } returns persisted
        every { paymentOrderDomainEventMapper.toPaymentOrderFinalized(persisted, any(), PaymentOrderStatus.CAPTURED) } returns succeededEvent
        every {
            eventPublisher.publishSync(
                aggregateId = persisted.paymentOrderId.value.toString(),
                data = succeededEvent,
                parentEventId = any(),
                traceId = any()
            )
        } returns mockk<EventEnvelope<PaymentOrderFinalized>>()

        service.processPspResult(event, order)

        verify(exactly = 1) { paymentOrderModificationPort.markAsCaptured(order) }
        verify(exactly = 1) { paymentOrderDomainEventMapper.toPaymentOrderFinalized(persisted, any(), PaymentOrderStatus.CAPTURED) }
        verify(exactly = 1) {
            eventPublisher.publishSync(
                aggregateId = persisted.paymentOrderId.value.toString(),
                data = succeededEvent,
                parentEventId = any(),
                traceId = any()
            )
        }
        verify(exactly = 0) { paymentOrderModificationPort.markAsCaptureFailed(any()) }
    }

    @Test
    fun `processPspResult publishes failure for capture failed status`() {
        val event = samplePspResultEvent(PaymentOrderStatus.CAPTURE_FAILED)
        val order = sampleOrder(status = PaymentOrderStatus.CAPTURE_REQUESTED)
        val persisted = sampleOrder(status = PaymentOrderStatus.CAPTURE_FAILED)
        val now = LocalDateTime.now(clock)
        val failedEvent = PaymentOrderFinalized.from(
            order = persisted,
            now = now,
            status = PaymentOrderStatus.CAPTURE_FAILED
        )

        every { paymentOrderModificationPort.markAsCaptureFailed(order) } returns persisted
        every { paymentOrderDomainEventMapper.toPaymentOrderFinalized(persisted, any(), PaymentOrderStatus.CAPTURE_FAILED) } returns failedEvent
        every {
            eventPublisher.publishSync(
                aggregateId = persisted.paymentOrderId.value.toString(),
                data = failedEvent,
                parentEventId = any(),
                traceId = any()
            )
        } returns mockk<EventEnvelope<PaymentOrderFinalized>>()

        service.processPspResult(event, order)

        verify(exactly = 1) { paymentOrderModificationPort.markAsCaptureFailed(order) }
        verify(exactly = 0) { paymentOrderModificationPort.markAsCaptured(any()) }
    }


    private fun sampleOrder(
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING
    ): PaymentOrder {
        val now = LocalDateTime.now(clock)
        return PaymentOrder.rehydrate(
            paymentOrderId = PaymentOrderId(123L),
            paymentId = PaymentId(456L),
            sellerId = SellerId("seller-789"),
            amount = Amount.of(1000L, Currency("EUR")),
            status = status,
            retryCount = 0,
            createdAt = now,
            updatedAt = now
        )
    }

    private fun samplePspResultEvent(pspStatus: PaymentOrderStatus): PaymentOrderPspResultUpdated {
        val now = LocalDateTime.now(clock)
        return PaymentOrderPspResultUpdated.fromJson(
            pOrderId = "123",
            pubOrderId = "paymentorder-123",
            pId = "456",
            pubPId = "payment-456",
            sellerId = "seller-789",
            amount = 1000L,
            currency = "EUR",
            pspStatus = pspStatus.name,
            latencyMs = 100L,
            timestamp = now
        )
    }
}

