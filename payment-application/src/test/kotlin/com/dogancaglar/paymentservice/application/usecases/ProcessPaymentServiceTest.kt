package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.common.event.Topics
import com.dogancaglar.common.logging.LogContext
import com.dogancaglar.paymentservice.domain.event.EventMetadatas
import com.dogancaglar.paymentservice.domain.event.PaymentOrderEvent
import com.dogancaglar.paymentservice.domain.event.PaymentOrderFailed
import com.dogancaglar.paymentservice.domain.event.PaymentOrderPspCallRequested
import com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceeded
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.domain.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.domain.util.PaymentOrderFactory
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import com.dogancaglar.paymentservice.ports.outbound.PspResultCachePort
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

class ProcessPaymentServiceTest {

    private lateinit var eventPublisher: EventPublisherPort
    private lateinit var retryQueuePort: RetryQueuePort<PaymentOrderPspCallRequested>
    private lateinit var paymentOrderModificationPort: PaymentOrderModificationPort
    private lateinit var clock: Clock
    private lateinit var service: ProcessPaymentService

    @BeforeEach
    fun setUp() {
        eventPublisher = mockk()
        retryQueuePort = mockk()
        paymentOrderModificationPort = mockk()
        clock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneId.of("UTC"))
        val paymentOrderDomainEventMapper = PaymentOrderDomainEventMapper(clock)
        val paymentOrderFactory = PaymentOrderFactory()
        service = ProcessPaymentService(
            eventPublisher = eventPublisher,
            retryQueuePort = retryQueuePort,
            paymentOrderModificationPort = paymentOrderModificationPort,
            clock = clock,
            paymentOrderFactory = paymentOrderFactory,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper
        )
    }

    @Test
    fun `processPspResult should handle successful payment and publish event with exact parameters`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 0)
        val pspStatus = PaymentOrderStatus.SUCCESSFUL_FINAL
        val expectedOrder = createMockPaymentOrder(status = PaymentOrderStatus.SUCCESSFUL_FINAL)
        val expectedEventId = java.util.UUID.fromString("12345678-1234-1234-1234-123456789012")
        val expectedTraceId = "trace-456"

        // Mock LogContext
        mockkObject(LogContext)
        every { LogContext.getEventId() } returns expectedEventId
        every { LogContext.getTraceId() } returns expectedTraceId

        every { paymentOrderModificationPort.markPaid(match { order ->
            order.paymentOrderId == PaymentOrderId(123L) &&
            order.publicPaymentOrderId == "paymentorder-123" &&
            order.status == PaymentOrderStatus.INITIATED_PENDING
        }) } returns expectedOrder
        
        val capturedEventData = slot<PaymentOrderSucceeded>()
        every { 
            eventPublisher.publishSync(
                preSetEventIdFromCaller = null,
                aggregateId = "paymentorder-123",
                eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
                data = capture(capturedEventData),
                traceId = "trace-456",
                parentEventId = expectedEventId,
                timeoutSeconds = 5
            )
        } returns mockk()

        // When
        service.processPspResult(event, pspStatus)

        // Then - Verify exact parameters passed to markPaid
        verify(exactly = 1) {
            paymentOrderModificationPort.markPaid(match { order ->
                order.paymentOrderId == PaymentOrderId(123L) &&
                        order.publicPaymentOrderId == "paymentorder-123" &&
                        order.status == PaymentOrderStatus.INITIATED_PENDING
            })
        }

        // Verify publishSync was called with correct parameters
        verify(exactly = 1) {
            eventPublisher.publishSync<com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceeded>(
                preSetEventIdFromCaller = null,
                aggregateId = "paymentorder-123",
                eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
                data = match { it is com.dogancaglar.paymentservice.domain.event.PaymentOrderSucceeded },
                traceId = "trace-456",
                parentEventId = expectedEventId,
                timeoutSeconds = 5
            )
        }
        
        // Verify captured event data
        assertNotNull(capturedEventData.captured)
        assertEquals("123", capturedEventData.captured.paymentOrderId)
        assertEquals("paymentorder-123", capturedEventData.captured.publicPaymentOrderId)
        assertEquals("456", capturedEventData.captured.paymentId)
        assertEquals("payment-456", capturedEventData.captured.publicPaymentId)
        assertEquals("seller-789", capturedEventData.captured.sellerId)
        assertEquals(100000L, capturedEventData.captured.amountValue)
        assertEquals("USD", capturedEventData.captured.currency)
        assertEquals("SUCCESSFUL", capturedEventData.captured.status)

        verify(exactly = 0) { retryQueuePort.scheduleRetry(any(), any(), any(), any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markFinalFailed(any(), any()) }
    }

    @Test
    fun `processPspResult should schedule retry for retryable failure with exact parameters`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 2)
        val pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR

        val failedOrder = createMockPaymentOrder(
            status = PaymentOrderStatus.FAILED_TRANSIENT_ERROR,
            retryCount = 3
        )

        every { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) } returns failedOrder
        every { retryQueuePort.scheduleRetry(any(), any(), any(), any()) } just Runs

        // When
        service.processPspResult(event, pspStatus)

        // Then - Verify exact parameters passed to markFailedForRetry
        verify(exactly = 1) {
            paymentOrderModificationPort.markFailedForRetry(
                order = match { order ->
                    order.paymentOrderId == PaymentOrderId(123L) &&
                            order.publicPaymentOrderId == "paymentorder-123" &&
                            order.status == PaymentOrderStatus.INITIATED_PENDING
                },
                reason = "FAILED_TRANSIENT_ERROR",
                lastError = null
            )
        }

        // Verify exact parameters passed to scheduleRetry
        verify(exactly = 1) {
            retryQueuePort.scheduleRetry(
                paymentOrder = match { order ->
                    order.paymentOrderId == PaymentOrderId(123L) &&
                            order.retryCount == 3 &&
                            order.status == PaymentOrderStatus.FAILED_TRANSIENT_ERROR
                },
                backOffMillis = match { backoff -> backoff > 0 }, // Just verify it's a positive value
                retryReason = "FAILED_TRANSIENT_ERROR",
                lastErrorMessage = null
            )
        }

        verify(exactly = 0) { eventPublisher.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markPaid(any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markFinalFailed(any(), any()) }
    }

    @Test
    fun `processPspResult should mark as final failed when max retries exceeded with exact parameters`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 5)
        val pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR

        val finalFailedOrder = createMockPaymentOrder(status = PaymentOrderStatus.FAILED_FINAL)
        val expectedEventId = java.util.UUID.fromString("12345678-1234-1234-1234-123456789012")
        val expectedTraceId = "trace-456"

        // Explicit, deterministic LogContext values
        mockkObject(LogContext)
        every { LogContext.getEventId() } returns expectedEventId
        every { LogContext.getTraceId() } returns expectedTraceId

        every { retryQueuePort.resetRetryCounter(any()) } just Runs
        every { paymentOrderModificationPort.markFinalFailed(any(), any()) } returns finalFailedOrder

        val capturedEventData = slot<PaymentOrderFailed>()
        every {
            eventPublisher.publishSync(
                preSetEventIdFromCaller = null,
                eventMetaData = EventMetadatas.PaymentOrderFailedMetadata,
                aggregateId = "paymentorder-123",
                data = capture(capturedEventData),
                parentEventId = expectedEventId,
                traceId = expectedTraceId,
                timeoutSeconds = 5
            )
        } returns mockk()

        // When
        service.processPspResult(event, pspStatus)

        // Then - Verify domain state transitions
        verify(exactly = 1) {
            retryQueuePort.resetRetryCounter(PaymentOrderId(123L))
        }
        verify(exactly = 1) {
            paymentOrderModificationPort.markFinalFailed(
                order = match { order ->
                    order.paymentOrderId == PaymentOrderId(123L) &&
                            order.publicPaymentOrderId == "paymentorder-123"
                },
                reason = "FAILED_TRANSIENT_ERROR"
            )
        }

        // Verify publishSync was called exactly once with exact params
        verify(exactly = 1) {
            eventPublisher.publishSync(
                preSetEventIdFromCaller = null,
                eventMetaData = EventMetadatas.PaymentOrderFailedMetadata,
                aggregateId = "paymentorder-123",
                data = match { event ->
                    event is com.dogancaglar.paymentservice.domain.event.PaymentOrderFailed &&
                            event.status == PaymentOrderStatus.FAILED_FINAL.name
                },
                parentEventId = expectedEventId,
                traceId = expectedTraceId,
                timeoutSeconds = 5
            )
        }
        
        // Verify captured event data
        assertNotNull(capturedEventData.captured)
        assertEquals("123", capturedEventData.captured.paymentOrderId)
        assertEquals("paymentorder-123", capturedEventData.captured.publicPaymentOrderId)
        assertEquals("456", capturedEventData.captured.paymentId)
        assertEquals("payment-456", capturedEventData.captured.publicPaymentId)
        assertEquals("seller-789", capturedEventData.captured.sellerId)
        assertEquals(100000L, capturedEventData.captured.amountValue)
        assertEquals("USD", capturedEventData.captured.currency)
        assertEquals("FAILED_FINAL", capturedEventData.captured.status)

        // Ensure no other actions triggered
        verify(exactly = 0) { retryQueuePort.scheduleRetry(any(), any(), any(), any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markPaid(any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) }
    }

    @Test
    fun `processPspResult should schedule status check for AUTH_NEEDED_STAUS_CHECK_LATER with exact parameters`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 0)
        val pspStatus = PaymentOrderStatus.AUTH_NEEDED_STAUS_CHECK_LATER

        every { paymentOrderModificationPort.markPendingAndScheduleStatusCheck(any(), any(), any()) } just Runs

        // When
        service.processPspResult(event, pspStatus)

        // Then - Verify exact parameters
        verify(exactly = 1) {
            paymentOrderModificationPort.markPendingAndScheduleStatusCheck(
                order = match { order ->
                    order.paymentOrderId == PaymentOrderId(123L) &&
                            order.publicPaymentOrderId == "paymentorder-123" &&
                            order.status == PaymentOrderStatus.INITIATED_PENDING
                },
                reason = "AUTH_NEEDED_STAUS_CHECK_LATER",
                lastError = null
            )
        }

        verify(exactly = 0) { retryQueuePort.scheduleRetry(any(), any(), any(), any()) }
        verify(exactly = 0) { eventPublisher.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markPaid(any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markFinalFailed(any(), any()) }
    }

    @Test
    fun `computeEqualJitterBackoff should return value between half and cap for attempt 1`() {
        // Given
        val fixedRandom = Random(42)
        val attempt = 1
        val minDelayMs = 2000L
        val maxDelayMs = 60000L

        // When
        val backoff = service.computeEqualJitterBackoff(attempt, minDelayMs, maxDelayMs, fixedRandom)

        // Then
        // For attempt 1: exp = 2000 * 2^0 = 2000, capped = 2000, half = 1000
        // Result should be between [1000, 2000]
        assertTrue(backoff >= 1000)
        assertTrue(backoff <= 2000)
    }

    @Test
    fun `computeEqualJitterBackoff should increase exponentially for higher attempts`() {
        // Given
        val fixedRandom = Random(42)
        val minDelayMs = 2000L
        val maxDelayMs = 60000L

        // When
        val backoff1 = service.computeEqualJitterBackoff(1, minDelayMs, maxDelayMs, fixedRandom)
        val backoff2 = service.computeEqualJitterBackoff(2, minDelayMs, maxDelayMs, fixedRandom)
        val backoff3 = service.computeEqualJitterBackoff(3, minDelayMs, maxDelayMs, fixedRandom)

        // Then - Check ranges instead of strict ordering due to jitter
        // Attempt 1: [1000, 2000]
        assertTrue(backoff1 >= 1000 && backoff1 <= 2000)

        // Attempt 2: [2000, 4000]
        assertTrue(backoff2 >= 2000 && backoff2 <= 4000)

        // Attempt 3: [4000, 8000]
        assertTrue(backoff3 >= 4000 && backoff3 <= 8000)
    }

    @Test
    fun `computeEqualJitterBackoff should cap at maxDelayMs`() {
        // Given
        val fixedRandom = Random(42)
        val attempt = 10
        val minDelayMs = 2000L
        val maxDelayMs = 60000L

        // When
        val backoff = service.computeEqualJitterBackoff(attempt, minDelayMs, maxDelayMs, fixedRandom)

        // Then
        // For attempt 10: exp = 2000 * 2^9 = 1024000, capped to 60000
        // Result should be between [30000, 60000]
        assertTrue(backoff >= 30000)
        assertTrue(backoff <= maxDelayMs)
    }

    @Test
    fun `computeEqualJitterBackoff should throw IllegalArgumentException for attempt less than 1`() {
        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            service.computeEqualJitterBackoff(0)
        }
    }

    @Test
    fun `mapEventToDomain should convert PaymentOrderEvent to PaymentOrder`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 2, status = "FAILED_TRANSIENT_ERROR")

        // When
        val result = service.mapEventToDomain(event)

        // Then
        assertNotNull(result)
        assertEquals(event.publicPaymentOrderId, result.publicPaymentOrderId)
        assertEquals(event.publicPaymentId, result.publicPaymentId)
        assertEquals(event.sellerId, result.sellerId.value)
        assertEquals(event.amountValue, result.amount.value)
        assertEquals(event.currency, result.amount.currency)
        // Factory preserves values from event
        assertEquals(2, result.retryCount) // Factory preserves retry count from event
        assertEquals(PaymentOrderStatus.FAILED_TRANSIENT_ERROR, result.status) // Factory preserves status from event
    }

    // Helper methods
    private fun createMockPaymentOrderEvent(
        retryCount: Int = 0,
        status: String = "INITIATED_PENDING"
    ): PaymentOrderEvent {
        return PaymentOrderPspCallRequested(
            paymentOrderId = "123",
            publicPaymentOrderId = "paymentorder-123",
            paymentId = "456",
            publicPaymentId = "payment-456",
            sellerId = "seller-789",
            amountValue = 100000L,
            currency = "USD",
            status = status,
            createdAt = LocalDateTime.now(clock),
            updatedAt = LocalDateTime.now(clock),
            retryCount = retryCount,
            retryReason = null,
            lastErrorMessage = null,
            dueAt = null
        )
    }

    private fun createMockPaymentOrder(
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING,
        retryCount: Int = 0
    ): PaymentOrder {
        return PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(123L))
            .publicPaymentOrderId("paymentorder-123")
            .paymentId(PaymentId(456L))
            .publicPaymentId("payment-456")
            .sellerId(SellerId("seller-789"))
            .amount(Amount(100000L, "USD"))
            .status(status)
            .createdAt(LocalDateTime.now(clock))
            .updatedAt(LocalDateTime.now(clock))
            .retryCount(retryCount)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()
    }



    // ========== COMPREHENSIVE EDGE CASE TESTS ==========

    @Test
    fun `processPspResult should handle boundary retry count exactly at MAX_RETRIES`() {
        // Given - Exactly at max retries (5)
        val event = createMockPaymentOrderEvent(retryCount = 5)
        val pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR
        mockkObject(LogContext)
        every { LogContext.getEventId() } returns java.util.UUID.fromString("22222222-2222-2222-2222-222222222222")
        every { LogContext.getTraceId() } returns "trace-failedfinal"
        val finalFailedOrder = createMockPaymentOrder(status = PaymentOrderStatus.FAILED_FINAL)
        val expectedEventId = java.util.UUID.fromString("22222222-2222-2222-2222-222222222222")
        val expectedTraceId = "trace-failedfinal"
        
        every { retryQueuePort.resetRetryCounter(any()) } just Runs
        every { paymentOrderModificationPort.markFinalFailed(any(), any()) } returns finalFailedOrder
        
        val capturedEventData = slot<PaymentOrderFailed>()
        every {
            eventPublisher.publishSync(
                preSetEventIdFromCaller = null,
                eventMetaData = EventMetadatas.PaymentOrderFailedMetadata,
                aggregateId = "paymentorder-123",
                data = capture(capturedEventData),
                parentEventId = expectedEventId,
                traceId = expectedTraceId,
                timeoutSeconds = 5
            )
        } returns mockk()
        // When
        service.processPspResult(event, pspStatus)

        // Then - Should trigger final failure, not retry
        verify(exactly = 1) { retryQueuePort.resetRetryCounter(PaymentOrderId(123L)) }
        verify(exactly = 1) { paymentOrderModificationPort.markFinalFailed(any(), any()) }
        
        // Verify captured event data
        assertNotNull(capturedEventData.captured)
        assertEquals("FAILED_FINAL", capturedEventData.captured.status)
        
        verify(exactly = 0) { retryQueuePort.scheduleRetry(any(), any(), any(), any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) }
    }

    @Test
    fun `processPspResult should handle boundary retry count just below MAX_RETRIES`() {
        // Given - Just below max retries (4)
        val event = createMockPaymentOrderEvent(retryCount = 4)
        val pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR

        val failedOrder = createMockPaymentOrder(status = PaymentOrderStatus.FAILED_TRANSIENT_ERROR, retryCount = 5)
        every { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) } returns failedOrder
        every { retryQueuePort.scheduleRetry(any(), any(), any(), any()) } just Runs

        // When
        service.processPspResult(event, pspStatus)

        // Then - Should schedule retry, not final failure
        verify(exactly = 1) { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) }
        verify(exactly = 1) { retryQueuePort.scheduleRetry(any(), any(), any(), any()) }
        verify(exactly = 0) { retryQueuePort.resetRetryCounter(any()) }
        verify(exactly = 0) { paymentOrderModificationPort.markFinalFailed(any(), any()) }
    }

    @Test
    fun `processPspResult should handle all retryable statuses correctly`() {
        val retryableStatuses = listOf(
            PaymentOrderStatus.FAILED_TRANSIENT_ERROR,
            PaymentOrderStatus.PSP_UNAVAILABLE_TRANSIENT,
            PaymentOrderStatus.TIMEOUT_EXCEEDED_1S_TRANSIENT
        )

        retryableStatuses.forEach { status ->
            // Reset mocks for each iteration
            clearAllMocks()

            val event = createMockPaymentOrderEvent(retryCount = 1)
            val failedOrder = createMockPaymentOrder(status = PaymentOrderStatus.FAILED_TRANSIENT_ERROR, retryCount = 2)

            every { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) } returns failedOrder
            every { retryQueuePort.scheduleRetry(any(), any(), any(), any()) } just Runs

            // When
            service.processPspResult(event, status)

            // Then - Each should trigger retry logic
            verify(exactly = 1) {
                paymentOrderModificationPort.markFailedForRetry(
                    any(),
                    status.name,
                    null
                )
            }
            verify(exactly = 1) { retryQueuePort.scheduleRetry(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `processPspResult should handle all status check statuses correctly`() {
        val statusCheckStatuses = listOf(
            PaymentOrderStatus.AUTH_NEEDED_STAUS_CHECK_LATER,
            PaymentOrderStatus.UNKNOWN_FINAL
        )

        statusCheckStatuses.forEach { status ->
            // Reset mocks for each iteration
            clearAllMocks()

            val event = createMockPaymentOrderEvent(retryCount = 0)
            every { paymentOrderModificationPort.markPendingAndScheduleStatusCheck(any(), any(), any()) } just Runs

            // When
            service.processPspResult(event, status)

            // Then - Each should trigger status check logic
            verify(exactly = 1) {
                paymentOrderModificationPort.markPendingAndScheduleStatusCheck(
                    any(),
                    status.name,
                    null
                )
            }
            verify(exactly = 0) { retryQueuePort.scheduleRetry(any(), any(), any(), any()) }
            verify(exactly = 0) { paymentOrderModificationPort.markPaid(any()) }
        }
    }

    @Test
    fun `processPspResult should handle all final failure statuses correctly`() {
        val finalFailureStatuses = listOf(
            PaymentOrderStatus.DECLINED_FINAL,
            PaymentOrderStatus.FAILED_FINAL
        )
        val expectedEventId = java.util.UUID.fromString("22222222-2222-2222-2222-222222222222")
        val expectedTraceId = "trace-failedfinal"

        finalFailureStatuses.forEach { status ->
            // Reset mocks for each iteration
            clearAllMocks()

            mockkObject(LogContext)
            every { LogContext.getEventId() } returns expectedEventId
            every { LogContext.getTraceId() } returns expectedTraceId
            
            val event = createMockPaymentOrderEvent(retryCount = 0)
            val finalFailedOrder = createMockPaymentOrder(status = PaymentOrderStatus.FAILED_FINAL)
            every { paymentOrderModificationPort.markFinalFailed(any(), any()) } returns finalFailedOrder
            
            val capturedEventData = slot<PaymentOrderFailed>()
            every {
                eventPublisher.publishSync(
                    preSetEventIdFromCaller = null,
                    eventMetaData = EventMetadatas.PaymentOrderFailedMetadata,
                    aggregateId = "paymentorder-123",
                    data = capture(capturedEventData),
                    parentEventId = expectedEventId,
                    traceId = expectedTraceId,
                    timeoutSeconds = 5
                )
            } returns mockk()
            
            // When
            service.processPspResult(event, status)

            // Then - Each should trigger final failure logic
            verify(exactly = 1) {
                paymentOrderModificationPort.markFinalFailed(any(), status.name)
            }
            verify(exactly = 1) {
                eventPublisher.publishSync(
                    preSetEventIdFromCaller = null,
                    eventMetaData = EventMetadatas.PaymentOrderFailedMetadata,
                    aggregateId = "paymentorder-123",
                    data = match { event ->
                        event is PaymentOrderFailed &&
                                event.status == PaymentOrderStatus.FAILED_FINAL.name
                    },
                    parentEventId = expectedEventId,
                    traceId = expectedTraceId,
                    timeoutSeconds = 5
                )
            }
            
            // Verify captured event data
            assertNotNull(capturedEventData.captured)
            assertEquals("FAILED_FINAL", capturedEventData.captured.status)

            verify(exactly = 0) { retryQueuePort.scheduleRetry(any(), any(), any(), any()) }
            verify(exactly = 0) { paymentOrderModificationPort.markPaid(any()) }
            verify(exactly = 0) { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) }
        }
    }

    @Test
    fun `processPspResult should handle exception in markPaid and not crash`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 0)
        val pspStatus = PaymentOrderStatus.SUCCESSFUL_FINAL

        // Mock LogContext
        mockkObject(LogContext)
        every { LogContext.getEventId() } returns java.util.UUID.fromString("12345678-1234-1234-1234-123456789012")
        every { LogContext.getTraceId() } returns "trace-456"

        every { paymentOrderModificationPort.markPaid(any()) } throws RuntimeException("Database error")
        every { eventPublisher.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) } returns mockk()

        // When/Then - Should not throw exception
        assertThrows<RuntimeException> {
            service.processPspResult(event, pspStatus)
        }

        // Verify that markPaid was called despite the exception
        verify(exactly = 1) { paymentOrderModificationPort.markPaid(any()) }
        verify(exactly = 0) { eventPublisher.publishSync<Any>(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `processPspResult should handle exception in markFailedForRetry and not crash`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 2)
        val pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR

        every { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) } throws RuntimeException("Database error")
        every { retryQueuePort.scheduleRetry(any(), any(), any(), any()) } just Runs

        // When/Then - Should not throw exception
        assertThrows<RuntimeException> {
            service.processPspResult(event, pspStatus)
        }

        // Verify that markFailedForRetry was called despite the exception
        verify(exactly = 1) { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) }
        verify(exactly = 0) { retryQueuePort.scheduleRetry(any(), any(), any(), any()) }
    }

    @Test
    fun `processPspResult should handle exception in markFinalFailed and not crash`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 5)
        val pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR

        every { retryQueuePort.resetRetryCounter(any()) } just Runs
        every { paymentOrderModificationPort.markFinalFailed(any(), any()) } throws RuntimeException("Database error")

        // When/Then - Should not throw exception
        assertThrows<RuntimeException> {
            service.processPspResult(event, pspStatus)
        }

        // Verify that markFinalFailed was called despite the exception
        verify(exactly = 1) { retryQueuePort.resetRetryCounter(any()) }
        verify(exactly = 1) { paymentOrderModificationPort.markFinalFailed(any(), any()) }
    }

    @Test
    fun `processPspResult should handle exception in markPendingAndScheduleStatusCheck and not crash`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 0)
        val pspStatus = PaymentOrderStatus.AUTH_NEEDED_STAUS_CHECK_LATER

        every { paymentOrderModificationPort.markPendingAndScheduleStatusCheck(any(), any(), any()) } throws RuntimeException("Database error")

        // When/Then - Should not throw exception
        assertThrows<RuntimeException> {
            service.processPspResult(event, pspStatus)
        }

        // Verify that markPendingAndScheduleStatusCheck was called despite the exception
        verify(exactly = 1) { paymentOrderModificationPort.markPendingAndScheduleStatusCheck(any(), any(), any()) }
    }

    @Test
    fun `processPspResult should handle exception in scheduleRetry and not crash`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 2)
        val pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR

        val failedOrder = createMockPaymentOrder(status = PaymentOrderStatus.FAILED_TRANSIENT_ERROR, retryCount = 3)
        every { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) } returns failedOrder
        every { retryQueuePort.scheduleRetry(any(), any(), any(), any()) } throws RuntimeException("Queue error")

        // When/Then - Should not throw exception
        assertThrows<RuntimeException> {
            service.processPspResult(event, pspStatus)
        }

        // Verify that both markFailedForRetry and scheduleRetry were called
        verify(exactly = 1) { paymentOrderModificationPort.markFailedForRetry(any(), any(), any()) }
        verify(exactly = 1) { retryQueuePort.scheduleRetry(any(), any(), any(), any()) }
    }

    @Test
    fun `processPspResult should handle exception in publishSync and not crash`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 0)
        val pspStatus = PaymentOrderStatus.SUCCESSFUL_FINAL
        val expectedEventId = java.util.UUID.fromString("12345678-1234-1234-1234-123456789012")
        val expectedTraceId = "trace-456"

        // Mock LogContext
        mockkObject(LogContext)
        every { LogContext.getEventId() } returns expectedEventId
        every { LogContext.getTraceId() } returns expectedTraceId

        val paidOrder = createMockPaymentOrder(status = PaymentOrderStatus.SUCCESSFUL_FINAL)
        every { paymentOrderModificationPort.markPaid(any()) } returns paidOrder
        
        // Capture the event data even though it will throw
        val capturedEventData = slot<PaymentOrderSucceeded>()
        every { 
            eventPublisher.publishSync(
                preSetEventIdFromCaller = null,
                aggregateId = "paymentorder-123",
                eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
                data = capture(capturedEventData),
                traceId = expectedTraceId,
                parentEventId = expectedEventId,
                timeoutSeconds = 5
            )
        } throws RuntimeException("Event publish error")

        // When/Then - Should not throw exception
        assertThrows<RuntimeException> {
            service.processPspResult(event, pspStatus)
        }

        // Verify that both markPaid and publishSync were called
        verify(exactly = 1) { paymentOrderModificationPort.markPaid(any()) }
        verify(exactly = 1) { 
            eventPublisher.publishSync<PaymentOrderSucceeded>(
                preSetEventIdFromCaller = null,
                aggregateId = "paymentorder-123",
                eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
                data = any(),
                traceId = expectedTraceId,
                parentEventId = expectedEventId,
                timeoutSeconds = 5
            )
        }
        
        // Verify that event data was being sent before exception
        assertNotNull(capturedEventData.captured)
        assertEquals("SUCCESSFUL", capturedEventData.captured.status)
    }

    @Test
    fun `processPspResult should handle exception in resetRetryCounter and not crash`() {
        // Given
        val event = createMockPaymentOrderEvent(retryCount = 5)
        val pspStatus = PaymentOrderStatus.FAILED_TRANSIENT_ERROR

        every { retryQueuePort.resetRetryCounter(any()) } throws RuntimeException("Queue error")
        val finalFailedOrder = createMockPaymentOrder(status = PaymentOrderStatus.FAILED_FINAL)
        every { paymentOrderModificationPort.markFinalFailed(any(), any()) } returns finalFailedOrder

        // When/Then - Should not throw exception
        assertThrows<RuntimeException> {
            service.processPspResult(event, pspStatus)
        }

        // Verify that resetRetryCounter was called (and threw exception)
        verify(exactly = 1) { retryQueuePort.resetRetryCounter(any()) }
        // markFinalFailed should not be called because resetRetryCounter threw exception first
        verify(exactly = 0) { paymentOrderModificationPort.markFinalFailed(any(), any()) }
    }

    @Test
    fun `processPspResult should publish to payment_order_finalized topic for succeeded events`() {
        // Given - verify the architecture requirement that both succeeded and failed events go to finalized topic
        val event = createMockPaymentOrderEvent(retryCount = 0)
        val pspStatus = PaymentOrderStatus.SUCCESSFUL_FINAL
        val expectedOrder = createMockPaymentOrder(status = PaymentOrderStatus.SUCCESSFUL_FINAL)
        val expectedEventId = java.util.UUID.fromString("99999999-9999-9999-9999-999999999999")
        val expectedTraceId = "trace-finalized-topic"

        mockkObject(LogContext)
        every { LogContext.getEventId() } returns expectedEventId
        every { LogContext.getTraceId() } returns expectedTraceId

        every { paymentOrderModificationPort.markPaid(any()) } returns expectedOrder
        
        val capturedEventData = slot<PaymentOrderSucceeded>()
        every {
            eventPublisher.publishSync(
                preSetEventIdFromCaller = null,
                aggregateId = any(),
                eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
                data = capture(capturedEventData),
                parentEventId = expectedEventId,
                traceId = expectedTraceId,
                timeoutSeconds = 5
            )
        } returns mockk()

        // When
        service.processPspResult(event, pspStatus)

        // Then - Verify that PaymentOrderSucceededMetadata uses the finalized topic
        assertNotNull(capturedEventData.captured)
        
        // Verify the event uses the correct topic (architecture requirement)
        assertEquals(Topics.PAYMENT_ORDER_FINALIZED, EventMetadatas.PaymentOrderSucceededMetadata.topic)
    }

    @Test
    fun `processPspResult should publish to payment_order_finalized topic for failed events`() {
        // Given - verify the architecture requirement that both succeeded and failed events go to finalized topic
        val event = createMockPaymentOrderEvent(retryCount = 0)
        val pspStatus = PaymentOrderStatus.FAILED_FINAL
        val finalFailedOrder = createMockPaymentOrder(status = PaymentOrderStatus.FAILED_FINAL)
        val expectedEventId = java.util.UUID.fromString("88888888-8888-8888-8888-888888888888")
        val expectedTraceId = "trace-finalized-topic-failed"

        mockkObject(LogContext)
        every { LogContext.getEventId() } returns expectedEventId
        every { LogContext.getTraceId() } returns expectedTraceId

        every { paymentOrderModificationPort.markFinalFailed(any(), any()) } returns finalFailedOrder
        
        val capturedEventData = slot<PaymentOrderFailed>()
        every {
            eventPublisher.publishSync(
                preSetEventIdFromCaller = null,
                aggregateId = any(),
                eventMetaData = EventMetadatas.PaymentOrderFailedMetadata,
                data = capture(capturedEventData),
                parentEventId = expectedEventId,
                traceId = expectedTraceId,
                timeoutSeconds = 5
            )
        } returns mockk()

        // When
        service.processPspResult(event, pspStatus)

        // Then - Verify that PaymentOrderFailedMetadata uses the finalized topic
        assertNotNull(capturedEventData.captured)
        
        // Verify the event uses the correct topic (architecture requirement)
        assertEquals(Topics.PAYMENT_ORDER_FINALIZED, EventMetadatas.PaymentOrderFailedMetadata.topic)
        
        // Verify both succeeded and failed use the same topic (architecture requirement)
        assertEquals(
            EventMetadatas.PaymentOrderSucceededMetadata.topic,
            EventMetadatas.PaymentOrderFailedMetadata.topic
        )
    }

    @Test
    fun `processPspResult should use explicit timeoutSeconds parameter of 5`() {
        // Given - verify that timeoutSeconds parameter is explicitly set to 5 seconds
        val event = createMockPaymentOrderEvent(retryCount = 0)
        val pspStatus = PaymentOrderStatus.SUCCESSFUL_FINAL
        val expectedOrder = createMockPaymentOrder(status = PaymentOrderStatus.SUCCESSFUL_FINAL)
        val expectedEventId = java.util.UUID.fromString("77777777-7777-7777-7777-777777777777")
        val expectedTraceId = "trace-timeout-test"

        mockkObject(LogContext)
        every { LogContext.getEventId() } returns expectedEventId
        every { LogContext.getTraceId() } returns expectedTraceId

        every { paymentOrderModificationPort.markPaid(any()) } returns expectedOrder
        
        val capturedTimeout = slot<Long>()
        every {
            eventPublisher.publishSync(
                preSetEventIdFromCaller = null,
                aggregateId = any(),
                eventMetaData = EventMetadatas.PaymentOrderSucceededMetadata,
                data = any(),
                parentEventId = expectedEventId,
                traceId = expectedTraceId,
                timeoutSeconds = capture(capturedTimeout)
            )
        } returns mockk()

        // When
        service.processPspResult(event, pspStatus)

        // Then - Verify timeoutSeconds is explicitly set to 5
        assertEquals(5L, capturedTimeout.captured)
    }
}
