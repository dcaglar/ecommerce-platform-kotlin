package com.dogancaglar.paymentservice.adapter.outbound.serialization

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentOrder
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/**
 * Unit tests for JacksonSerializationAdapter.
 * 
 * Tests verify:
 * - Basic serialization/deserialization of domain objects
 * - Null handling
 * - Special characters and edge cases
 * - Nested objects
 * - Error handling
 */
class JacksonSerializationAdapterTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var adapter: JacksonSerializationAdapter

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().apply {
            registerModule(JavaTimeModule())
            registerKotlinModule()
        }
        adapter = JacksonSerializationAdapter(objectMapper)
    }

    // ==================== Basic Serialization Tests ====================

    @Test
    fun `toJson should serialize simple domain object`() {
        // Given
        val paymentOrder = createTestPaymentOrder()

        // When
        val json = adapter.toJson(paymentOrder)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("\"paymentOrderId\""))
        assertTrue(json.contains("\"publicPaymentOrderId\""))
        assertTrue(json.contains("\"status\""))
        assertTrue(json.contains("\"amount\""))
        assertTrue(json.contains("\"value\":10000"))
        assertTrue(json.contains("\"currency\":\"USD\""))
    }

    @Test
    fun `toJson should serialize nested objects correctly`() {
        // Given
        val paymentOrder = createTestPaymentOrderWithNestedData()

        // When
        val json = adapter.toJson(paymentOrder)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("\"paymentOrderId\""))
        assertTrue(json.contains("\"amount\""))
        assertTrue(json.contains("\"value\":25000"))
        assertTrue(json.contains("\"currency\":\"EUR\""))
        assertTrue(json.contains("\"retryCount\":2"))
        assertTrue(json.contains("\"retryReason\":\"PSP_TIMEOUT\""))
    }

    @Test
    fun `toJson should handle null values correctly`() {
        // Given
        val paymentOrder = createTestPaymentOrderWithNulls()

        // When
        val json = adapter.toJson(paymentOrder)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("\"retryReason\":null"))
        assertTrue(json.contains("\"lastErrorMessage\":null"))
        assertTrue(json.contains("\"paymentOrderId\":789"))
    }

    // ==================== Special Characters and Edge Cases ====================

    @Test
    fun `toJson should handle special characters in strings`() {
        // Given
        val specialChars = "Test with special chars: \"quotes\", 'apostrophes', \n newlines, \t tabs, \\ backslashes, / slashes"
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(1L))
            .publicPaymentOrderId(specialChars)
            .paymentId(PaymentId(1L))
            .publicPaymentId("payment-1")
            .sellerId(SellerId("seller_1"))
            .amount(Amount.of(10000L, Currency("USD")))
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .retryCount(0)
            .retryReason("Error: \"Connection failed\" with 'timeout'")
            .lastErrorMessage(null)
            .buildFromPersistence()

        // When
        val json = adapter.toJson(paymentOrder)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("\"publicPaymentOrderId\""))
        assertTrue(json.contains("\"retryReason\""))
        
        // JSON serialization successful
    }

    @Test
    fun `toJson should handle unicode characters`() {
        // Given
        val unicodeText = "Test with unicode: 中文, العربية, русский, 🚀 emoji, € currency"
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(1L))
            .publicPaymentOrderId(unicodeText)
            .paymentId(PaymentId(1L))
            .publicPaymentId("payment-1")
            .sellerId(SellerId("seller-测试-123"))
            .amount(Amount.of(10000L, Currency("USD")))
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()

        // When
        val json = adapter.toJson(paymentOrder)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("中文"))
        assertTrue(json.contains("🚀"))
        assertTrue(json.contains("€"))
        assertTrue(json.contains("seller-测试-123"))
    }

    @Test
    fun `toJson should handle empty strings`() {
        // Given
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(1L))
            .publicPaymentOrderId("")
            .paymentId(PaymentId(1L))
            .publicPaymentId("payment-1")
            .sellerId(SellerId("seller_1"))
            .amount(Amount.of(10000L, Currency("USD")))
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .retryCount(0)
            .retryReason("")
            .lastErrorMessage(null)
            .buildFromPersistence()

        // When
        val json = adapter.toJson(paymentOrder)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("\"publicPaymentOrderId\":\"\""))
        assertTrue(json.contains("\"retryReason\":\"\""))
    }

    // ==================== Date and Time Handling ====================

    @Test
    fun `toJson should serialize LocalDateTime correctly`() {
        // Given
        val fixedTime = LocalDateTime.of(2023, 12, 25, 14, 30, 45, 123_456_789)
        val paymentOrder = PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(1L))
            .publicPaymentOrderId("test_order_1")
            .paymentId(PaymentId(1L))
            .publicPaymentId("payment-1")
            .sellerId(SellerId("seller_1"))
            .amount(Amount.of(10000L, Currency("USD")))
            .status(PaymentOrderStatus.INITIATED_PENDING)
            .createdAt(fixedTime)
            .updatedAt(fixedTime)
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()

        // When
        val json = adapter.toJson(paymentOrder)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("\"createdAt\""))
        assertTrue(json.contains("\"updatedAt\""))
    }

    // ==================== Complex Object Structures ====================

    @Test
    fun `toJson should handle complex nested structures`() {
        // Given
        val complexData = mapOf(
            "metadata" to mapOf(
                "source" to "mobile-app",
                "version" to "1.2.3",
                "features" to listOf("feature1", "feature2", "feature3")
            ),
            "user" to mapOf(
                "id" to "user-123",
                "preferences" to mapOf(
                    "language" to "en",
                    "currency" to "USD"
                )
            )
        )

        // When
        val json = adapter.toJson(complexData)

        // Then
        assertNotNull(json)
        assertTrue(json.contains("\"source\":\"mobile-app\""))
        assertTrue(json.contains("\"version\":\"1.2.3\""))
        assertTrue(json.contains("\"features\":[\"feature1\",\"feature2\",\"feature3\"]"))
        assertTrue(json.contains("\"user\":{\"id\":\"user-123\""))
    }

    @Test
    fun `toJson should handle collections correctly`() {
        // Given
        val list = listOf(
            createTestPaymentOrder(id = 1L),
            createTestPaymentOrder(id = 2L),
            createTestPaymentOrder(id = 3L)
        )

        // When
        val json = adapter.toJson(list)

        // Then
        assertNotNull(json)
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("\"paymentOrderId\":1"))
        assertTrue(json.contains("\"paymentOrderId\":2"))
        assertTrue(json.contains("\"paymentOrderId\":3"))
    }

    // ==================== Error Handling ====================

    @Test
    fun `toJson should throw exception for circular references`() {
        // Given
        val circularRef = createCircularReferenceObject()

        // When/Then
        assertThrows<Exception> {
            adapter.toJson(circularRef)
        }
    }

    @Test
    fun `toJson should handle very large objects`() {
        // Given
        val largeObject = createLargeObject()

        // When
        val json = adapter.toJson(largeObject)

        // Then
        assertNotNull(json)
        assertTrue(json.length > 10000) // Should be a large JSON string
    }

    // ==================== Integration with Mock ObjectMapper ====================

    @Test
    fun `toJson should delegate to ObjectMapper`() {
        // Given
        val mockObjectMapper = mockk<ObjectMapper>(relaxed = true)
        val testAdapter = JacksonSerializationAdapter(mockObjectMapper)
        val testObject = createTestPaymentOrder()
        val expectedJson = """{"test":"value"}"""
        
        every { mockObjectMapper.writeValueAsString(testObject) } returns expectedJson

        // When
        val result = testAdapter.toJson(testObject)

        // Then
        assertEquals(expectedJson, result)
        verify(exactly = 1) { mockObjectMapper.writeValueAsString(testObject) }
    }

    @Test
    fun `toJson should propagate ObjectMapper exceptions`() {
        // Given
        val mockObjectMapper = mockk<ObjectMapper>(relaxed = true)
        val testAdapter = JacksonSerializationAdapter(mockObjectMapper)
        val testObject = createTestPaymentOrder()
        
        every { mockObjectMapper.writeValueAsString(testObject) } throws RuntimeException("Serialization failed")

        // When/Then
        assertThrows<RuntimeException> {
            testAdapter.toJson(testObject)
        }
    }

    // ==================== Helper Methods ====================

    private fun createTestPaymentOrder(
        id: Long = 123L,
        retryCount: Int = 0,
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING
    ): PaymentOrder {
        return PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(id))
            .publicPaymentOrderId("po-$id")
            .paymentId(PaymentId(999L))
            .publicPaymentId("pay-999")
            .sellerId(SellerId("111"))
            .amount(Amount.of(10000L, Currency("USD")))
            .status(status)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .retryCount(retryCount)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()
    }

    private fun createTestPaymentOrderWithNestedData(): PaymentOrder {
        return PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(456L))
            .publicPaymentOrderId("po-456")
            .paymentId(PaymentId(888L))
            .publicPaymentId("pay-888")
            .sellerId(SellerId("seller-456"))
            .amount(Amount.of(25000L, Currency("EUR")))
            .status(PaymentOrderStatus.FAILED_TRANSIENT_ERROR)
            .createdAt(LocalDateTime.of(2023, 6, 15, 10, 30))
            .updatedAt(LocalDateTime.of(2023, 6, 15, 10, 35))
            .retryCount(2)
            .retryReason("PSP_TIMEOUT")
            .lastErrorMessage("Connection timeout after 30 seconds")
            .buildFromPersistence()
    }

    private fun createTestPaymentOrderWithNulls(): PaymentOrder {
        return PaymentOrder.builder()
            .paymentOrderId(PaymentOrderId(789L))
            .publicPaymentOrderId("po-789")
            .paymentId(PaymentId(777L))
            .publicPaymentId("pay-777")
            .sellerId(SellerId("seller-789"))
            .amount(Amount.of(5000L, Currency("GBP")))
            .status(PaymentOrderStatus.SUCCESSFUL_FINAL)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .retryCount(0)
            .retryReason(null)
            .lastErrorMessage(null)
            .buildFromPersistence()
    }

    private fun createCircularReferenceObject(): Any {
        val obj1 = mutableMapOf<String, Any>()
        val obj2 = mutableMapOf<String, Any>()
        obj1["ref"] = obj2
        obj2["ref"] = obj1
        return obj1
    }

    private fun createLargeObject(): Map<String, Any> {
        val largeMap = mutableMapOf<String, Any>()
        repeat(1000) { i ->
            largeMap["key$i"] = "value$i".repeat(10) // Create large strings
        }
        return largeMap
    }
}
