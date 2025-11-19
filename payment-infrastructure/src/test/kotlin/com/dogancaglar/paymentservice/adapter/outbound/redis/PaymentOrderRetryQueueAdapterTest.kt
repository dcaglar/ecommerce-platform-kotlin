package com.dogancaglar.paymentservice.adapter.outbound.redis

import com.dogancaglar.common.event.EventEnvelope
import com.dogancaglar.common.event.EventEnvelopeFactory
import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.paymentservice.application.commands.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

/**
 * Unit tests for PaymentOrderRetryQueueAdapter using MockK.
 * 
 * Tests verify:
 * - Retry scheduling with event envelope creation
 * - Serialization and deserialization
 * - Inflight management
 * - Error handling (poison messages)
 * - Retry counter operations
 */
class PaymentOrderRetryQueueAdapterTest {

    private lateinit var paymentOrderRetryRedisCache: PaymentOrderRetryRedisCache
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var objectMapper: ObjectMapper
    private lateinit var adapter: PaymentOrderRetryQueueAdapter

    @BeforeEach
    fun setUp() {
        paymentOrderRetryRedisCache = mockk(relaxed = true)
        meterRegistry = SimpleMeterRegistry()
        objectMapper = ObjectMapper().apply {
            registerModule(JavaTimeModule())
            registerKotlinModule()
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        
        // Mock zsetSize for gauge registration
        every { paymentOrderRetryRedisCache.zsetSize() } returns 0L
        
        // Mock EventLogContext
        mockkObject(EventLogContext)
        every { EventLogContext.getTraceId() } returns "test-trace-id"
        every { EventLogContext.getEventId() } returns null
        
        adapter = PaymentOrderRetryQueueAdapter(
            paymentOrderRetryRedisCache,
            meterRegistry,
            objectMapper,
            PaymentOrderDomainEventMapper()
        )
    }

    private fun createTestPaymentOrder(
        id: Long = 123L,
        retryCount: Int = 0,
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = createdAt
    ): PaymentOrder =
        PaymentOrder.rehydrate(
            paymentOrderId = PaymentOrderId(id),
            paymentId = PaymentId(999L),
            sellerId = SellerId("111"),
            amount = Amount.of(10000L, Currency("USD")), // cents
            status = status,
            retryCount = retryCount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    // ==================== Schedule Retry Tests ====================

    @Test
    fun `scheduleRetry should serialize and schedule event envelope`() {
        // Given
        val paymentOrder = createTestPaymentOrder(id = 123L, retryCount = 1)
        val backOffMillis = 5000L

        // When
        adapter.scheduleRetry(paymentOrder, backOffMillis)

        // Then
        verify(exactly = 1) {
            paymentOrderRetryRedisCache.scheduleRetry(
                match { json ->
                    try {
                        val node = objectMapper.readTree(json)
                        node["eventType"].asText() == "payment_order_capture_requested" &&
                            node["data"]["publicPaymentOrderId"].asText() == paymentOrder.paymentOrderId.toPublicPaymentOrderId() &&
                            node["data"]["publicPaymentId"].asText() == paymentOrder.paymentId.toPublicPaymentId()
                    } catch (ex: Exception) {
                        false
                    }
                },
                match { score -> score > System.currentTimeMillis() }
            )
        }
    }

    @Test
    fun `scheduleRetry should calculate correct retry time`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        val backOffMillis = 10000L
        val startTime = System.currentTimeMillis()

        // When
        adapter.scheduleRetry(paymentOrder, backOffMillis)

        // Then
        verify(exactly = 1) {
            paymentOrderRetryRedisCache.scheduleRetry(
                any(),
                match { score ->
                    val expectedTime = startTime + backOffMillis
                    score >= expectedTime && score <= expectedTime + 100 // Allow 100ms tolerance
                }
            )
        }
    }

    @Test
    fun `scheduleRetry should include retry count from payment order`() {
        // Given
        val paymentOrder = createTestPaymentOrder(retryCount = 3)
        val backOffMillis = 1000L

        // When
        adapter.scheduleRetry(paymentOrder, backOffMillis)

        // Then
        verify(exactly = 1) {
            paymentOrderRetryRedisCache.scheduleRetry(
                match { json ->
                    json.contains("\"attempt\":3")
                },
                any()
            )
        }
    }

    @Test
    fun `scheduleRetry should handle exceptions and rethrow`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        every { 
            paymentOrderRetryRedisCache.scheduleRetry(any(), any())
        } throws RuntimeException("Redis connection failed")

        // When/Then
        assertThrows(RuntimeException::class.java) {
            adapter.scheduleRetry(paymentOrder, 1000L)
        }
    }

    // ==================== Poll Inflight Tests ====================

    @Test
    fun `pollDueRetriesToInflight should return empty list when no items`() {
        // Given
        every { paymentOrderRetryRedisCache.popDueToInflight(any()) } returns emptyList()

        // When
        val result = adapter.pollDueRetriesToInflight(100)

        // Then
        assertTrue(result.isEmpty())
        verify(exactly = 1) { paymentOrderRetryRedisCache.popDueToInflight(100) }
    }

    @Test
    fun `pollDueRetriesToInflight should deserialize valid event envelopes`() {
        // Given
        val now = LocalDateTime.now()
        val paymentOrder = createTestPaymentOrder(id = 123L, retryCount = 1)
        val captureCommand = PaymentOrderCaptureCommand.from(paymentOrder, attempt = 1, now = now)
        val eventEnvelope = EventEnvelopeFactory.envelopeFor(
            data = captureCommand,
            aggregateId = captureCommand.paymentOrderId,
            traceId = UUID.randomUUID().toString(),
            parentEventId = null
        )
        
        val jsonBytes = objectMapper.writeValueAsBytes(eventEnvelope)
        every { paymentOrderRetryRedisCache.popDueToInflight(any()) } returns listOf(jsonBytes)

        // When
        val result = adapter.pollDueRetriesToInflight(100)

        // Then
        assertEquals(1, result.size)
        val retryItem = result[0]
        assertEquals(eventEnvelope.eventId, retryItem.envelope.eventId)
        assertEquals("123", retryItem.envelope.data.paymentOrderId)
        assertArrayEquals(jsonBytes, retryItem.raw)
    }

    @Test
    fun `pollDueRetriesToInflight should handle poison messages by removing from inflight`() {
        // Given
        val invalidJson = "{ invalid json }".toByteArray()
        every { paymentOrderRetryRedisCache.popDueToInflight(any()) } returns listOf(invalidJson)
        every { paymentOrderRetryRedisCache.removeFromInflight(any()) } just Runs

        // When
        val result = adapter.pollDueRetriesToInflight(100)

        // Then
        assertTrue(result.isEmpty(), "Poison messages should not be returned")
        verify(exactly = 1) { paymentOrderRetryRedisCache.removeFromInflight(invalidJson) }
    }

    @Test
    fun `pollDueRetriesToInflight should handle mixed valid and invalid messages`() {
        // Given
        val now = LocalDateTime.now()
        val paymentOrder = createTestPaymentOrder(id = 123L, retryCount = 1)
        val captureCommand = PaymentOrderCaptureCommand.from(paymentOrder, attempt = 1, now = now)
        val validEnvelope = EventEnvelopeFactory.envelopeFor(
            data = captureCommand,
            aggregateId = captureCommand.paymentOrderId,
            traceId = UUID.randomUUID().toString(),
            parentEventId = null
        )
        
        val validJson = objectMapper.writeValueAsBytes(validEnvelope)
        val invalidJson = "{ broken".toByteArray()
        
        every { paymentOrderRetryRedisCache.popDueToInflight(any()) } returns listOf(validJson, invalidJson)
        every { paymentOrderRetryRedisCache.removeFromInflight(any()) } just Runs

        // When
        val result = adapter.pollDueRetriesToInflight(100)

        // Then
        assertEquals(1, result.size, "Only valid message should be returned")
        assertEquals(validEnvelope.eventId, result[0].envelope.eventId)
        verify(exactly = 1) { paymentOrderRetryRedisCache.removeFromInflight(invalidJson) }
    }

    @Test
    fun `pollDueRetriesToInflight should respect maxBatchSize parameter`() {
        // Given
        val maxBatchSize = 50L

        // When
        adapter.pollDueRetriesToInflight(maxBatchSize)

        // Then
        verify(exactly = 1) { paymentOrderRetryRedisCache.popDueToInflight(maxBatchSize) }
    }

    // ==================== Retry Counter Tests ====================

    @Test
    fun `getRetryCount should delegate to cache`() {
        // Given
        val paymentOrderId = PaymentOrderId(456L)
        every { paymentOrderRetryRedisCache.getRetryCount(456L) } returns 3

        // When
        val count = adapter.getRetryCount(paymentOrderId)

        // Then
        assertEquals(3, count)
        verify(exactly = 1) { paymentOrderRetryRedisCache.getRetryCount(456L) }
    }

    @Test
    fun `resetRetryCounter should delegate to cache`() {
        // Given
        val paymentOrderId = PaymentOrderId(789L)
        every { paymentOrderRetryRedisCache.resetRetryCounter(789L) } just Runs

        // When
        adapter.resetRetryCounter(paymentOrderId)

        // Then
        verify(exactly = 1) { paymentOrderRetryRedisCache.resetRetryCounter(789L) }
    }

    // ==================== Inflight Management Tests ====================

    @Test
    fun `removeFromInflight should delegate to cache`() {
        // Given
        val raw = "some-json".toByteArray()

        // When
        adapter.removeFromInflight(raw)

        // Then
        verify(exactly = 1) { paymentOrderRetryRedisCache.removeFromInflight(raw) }
    }

    @Test
    fun `reclaimInflight should delegate to cache with default parameter`() {
        // When
        adapter.reclaimInflight()

        // Then
        verify(exactly = 1) { paymentOrderRetryRedisCache.reclaimInflight(60_000) }
    }

    @Test
    fun `reclaimInflight should delegate to cache with custom parameter`() {
        // When
        adapter.reclaimInflight(olderThanMs = 120_000)

        // Then
        verify(exactly = 1) { paymentOrderRetryRedisCache.reclaimInflight(120_000) }
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    fun `scheduleRetry should handle zero backoff time`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        val backOffMillis = 0L
        val startTime = System.currentTimeMillis()

        // When
        adapter.scheduleRetry(paymentOrder, backOffMillis)

        // Then - should schedule for immediate retry
        verify(exactly = 1) {
            paymentOrderRetryRedisCache.scheduleRetry(
                any(),
                match { score -> 
                    score >= startTime && score <= startTime + 100
                }
            )
        }
    }

    @Test
    fun `scheduleRetry should handle large backoff time`() {
        // Given
        val paymentOrder = createTestPaymentOrder()
        val backOffMillis = 86400000L // 24 hours

        // When
        adapter.scheduleRetry(paymentOrder, backOffMillis)

        // Then
        verify(exactly = 1) {
            paymentOrderRetryRedisCache.scheduleRetry(
                any(),
                match { score -> 
                    val expectedTime = System.currentTimeMillis() + backOffMillis
                    score >= expectedTime - 100 && score <= expectedTime + 100
                }
            )
        }
    }

    @Test
    fun `pollDueRetriesToInflight should handle empty byte array`() {
        // Given
        val emptyBytes = ByteArray(0)
        every { paymentOrderRetryRedisCache.popDueToInflight(any()) } returns listOf(emptyBytes)
        every { paymentOrderRetryRedisCache.removeFromInflight(any()) } just Runs

        // When
        val result = adapter.pollDueRetriesToInflight(100)

        // Then
        assertTrue(result.isEmpty())
        verify(exactly = 1) { paymentOrderRetryRedisCache.removeFromInflight(emptyBytes) }
    }

    @Test
    fun `gauge should be registered on adapter creation`() {
        // Given/When - adapter created in setUp
        val gauge = meterRegistry.find("redis_retry_zset_size").gauge()

        // Then
        assertNotNull(gauge)
        assertEquals("Number of entries pending in the Redis retry ZSet", gauge?.id?.description)
    }

    @Test
    fun `scheduleRetry should serialize envelope with correct event type`() {
        // Given
        val paymentOrder = createTestPaymentOrder()

        // When
        adapter.scheduleRetry(paymentOrder, 1000L)

        // Then
        verify(exactly = 1) {
            paymentOrderRetryRedisCache.scheduleRetry(
                match { json ->
                    val envelope = objectMapper.readValue(
                        json,
                        object : TypeReference<EventEnvelope<PaymentOrderCaptureCommand>>() {}
                    )
                    envelope.eventType == "payment_order_capture_requested"
                },
                any()
            )
        }
    }
}

