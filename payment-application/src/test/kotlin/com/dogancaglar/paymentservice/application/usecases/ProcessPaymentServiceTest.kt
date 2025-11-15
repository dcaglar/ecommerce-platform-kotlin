package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.events.PaymentOrderEvent
import com.dogancaglar.paymentservice.application.events.PaymentOrderFailed
import com.dogancaglar.paymentservice.application.events.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.application.metadata.EventMetadatas
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

        mockkObject(LogContext)
        every { LogContext.getEventId() } returns null
        every { LogContext.getTraceId() } returns null

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
        unmockkObject(LogContext)
    }

    @Test
    fun `processPspResult publishes success for captured status`() {
        val event = sampleEvent(PaymentOrderStatus.CAPTURED)
        val order = sampleOrder()
        val persisted = sampleOrder(status = PaymentOrderStatus.CAPTURED)
        val succeededEvent = PaymentOrderSucceeded.create(
            paymentOrderId = persisted.paymentOrderId.value.toString(),
            paymentId = persisted.paymentId.value.toString(),
            sellerId = persisted.sellerId.value,
            amountValue = persisted.amount.quantity,
            currency = persisted.amount.currency.currencyCode,
            status = persisted.status.name
        )
        every { paymentOrderDomainEventMapper.fromEvent(event) } returns order
        every { paymentOrderModificationPort.markAsCaptured(order) } returns persisted
        every { paymentOrderDomainEventMapper.toPaymentOrderSucceeded(persisted) } returns succeededEvent
        every {
            eventPublisher.publishSync(
                aggregateId = persisted.paymentOrderId.value.toString(),
                eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
                data = succeededEvent,
                parentEventId = any(),
                traceId = any()
            )
        } returns mockk<EventEnvelope<PaymentOrderSucceeded>>()

        service.processPspResult(event, PaymentOrderStatus.CAPTURED)

        verify(exactly = 1) { paymentOrderModificationPort.markAsCaptured(order) }
        verify(exactly = 1) { paymentOrderDomainEventMapper.toPaymentOrderSucceeded(persisted) }
        verify(exactly = 1) {
            eventPublisher.publishSync(
                aggregateId = persisted.paymentOrderId.value.toString(),
                eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
                data = succeededEvent,
                parentEventId = any(),
                traceId = any()
            )
        }
        verify(exactly = 0) { paymentOrderModificationPort.markAsCaptureFailed(any()) }
    }

    @Test
    fun `processPspResult publishes failure for capture failed status`() {
        val event = sampleEvent(PaymentOrderStatus.CAPTURE_FAILED)
        val order = sampleOrder()
        val persisted = sampleOrder(status = PaymentOrderStatus.CAPTURE_FAILED)
        val failedEvent = paymentOrderFailedEvent(persisted)

        every { paymentOrderDomainEventMapper.fromEvent(event) } returns order
        every { paymentOrderModificationPort.markAsCaptureFailed(order) } returns persisted
        every { paymentOrderDomainEventMapper.toPaymentOrderFailed(persisted) } returns failedEvent
        every {
            eventPublisher.publishSync(
                aggregateId = persisted.paymentOrderId.value.toString(),
                eventMetaData = EventMetadatas.PaymentOrderFailedMetadata,
                data = failedEvent,
                parentEventId = any(),
                traceId = any()
            )
        } returns mockk<EventEnvelope<PaymentOrderFailed>>()

        service.processPspResult(event, PaymentOrderStatus.CAPTURE_FAILED)

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

    private fun sampleEvent(status: PaymentOrderStatus): PaymentOrderEvent =
        object : PaymentOrderEvent {
            override val paymentOrderId: String = "123"
            override val publicPaymentOrderId: String = "paymentorder-123"
            override val paymentId: String = "456"
            override val publicPaymentId: String = "payment-456"
            override val sellerId: String = "seller-789"
            override val amountValue: Long = 1000L
            override val currency: String = "EUR"
            override val status: String = status.name
            override val createdAt: LocalDateTime = LocalDateTime.now(clock)
            override val updatedAt: LocalDateTime = LocalDateTime.now(clock)
            override val retryCount: Int = 0
        }

    private fun paymentOrderFailedEvent(order: PaymentOrder): PaymentOrderFailed =
        PaymentOrderFailed.create(
            paymentOrderId = order.paymentOrderId.value.toString(),
            paymentId = order.paymentId.value.toString(),
            sellerId = order.sellerId.value,
            amountValue = order.amount.quantity,
            currency = order.amount.currency.currencyCode,
            status = order.status.name
        )
}

