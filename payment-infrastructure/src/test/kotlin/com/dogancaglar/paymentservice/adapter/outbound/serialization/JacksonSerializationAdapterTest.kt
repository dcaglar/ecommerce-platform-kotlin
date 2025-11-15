package com.dogancaglar.paymentservice.adapter.outbound.serialization

import com.dogancaglar.paymentservice.application.util.toPublicPaymentOrderId
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

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

    @Test
    fun `toJson serializes payment order`() {
        val order = createTestPaymentOrder()

        val json = adapter.toJson(order)

        assertNotNull(json)
        assertTrue(json.contains("\"paymentOrderId\":${order.paymentOrderId.value}"))
        assertTrue(json.contains("\"paymentId\":${order.paymentId.value}"))
        assertTrue(json.contains("\"retryCount\":${order.retryCount}"))
        assertTrue(json.contains("\"currency\":\"${order.amount.currency.currencyCode}\""))
    }

    @Test
    fun `toJson serializes maps and lists`() {
        val payload = mapOf(
            "metadata" to mapOf("source" to "mobile-app", "version" to "1.2.3"),
            "items" to listOf(
                mapOf("sku" to "A1", "qty" to 2),
                mapOf("sku" to "B2", "qty" to 1)
            )
        )

        val json = adapter.toJson(payload)

        assertNotNull(json)
        assertTrue(json.contains("\"metadata\""))
        assertTrue(json.contains("\"items\""))
        assertTrue(json.contains("\"sku\":\"A1\""))
    }

    @Test
    fun `toJson serializes LocalDateTime values`() {
        val timestamp = LocalDateTime.of(2024, 1, 1, 12, 0)

        val json = adapter.toJson(mapOf("createdAt" to timestamp))

        assertNotNull(json)
        val node = objectMapper.readTree(json)
        assertTrue(node.has("createdAt"))
    }

    @Test
    fun `toJson delegates to configured ObjectMapper`() {
        val mockMapper = mockk<ObjectMapper>(relaxed = true)
        val testAdapter = JacksonSerializationAdapter(mockMapper)
        val order = createTestPaymentOrder()
        val expected = """{"ok":true}"""

        every { mockMapper.writeValueAsString(order) } returns expected

        val result = testAdapter.toJson(order)

        assertEquals(expected, result)
        verify(exactly = 1) { mockMapper.writeValueAsString(order) }
    }

    @Test
    fun `toJson propagates ObjectMapper exceptions`() {
        val mockMapper = mockk<ObjectMapper>(relaxed = true)
        val testAdapter = JacksonSerializationAdapter(mockMapper)
        val order = createTestPaymentOrder()

        every { mockMapper.writeValueAsString(order) } throws RuntimeException("boom")

        assertThrows<RuntimeException> {
            testAdapter.toJson(order)
        }
    }

    private fun createTestPaymentOrder(
        id: Long = 123L,
        retryCount: Int = 0,
        status: PaymentOrderStatus = PaymentOrderStatus.INITIATED_PENDING
    ): PaymentOrder =
        PaymentOrder.rehydrate(
            paymentOrderId = PaymentOrderId(id),
            paymentId = PaymentId(999L),
            sellerId = SellerId("111"),
            amount = Amount.of(10000L, Currency("USD")),
            status = status,
            retryCount = retryCount,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
}

