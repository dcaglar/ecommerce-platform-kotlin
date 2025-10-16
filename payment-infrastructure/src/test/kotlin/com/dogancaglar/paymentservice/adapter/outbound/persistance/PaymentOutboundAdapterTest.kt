package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.PaymentEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.PaymentMapper
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/**
 * Unit tests for PaymentOutboundAdapter using MockK.
 * 
 * Tests verify:
 * - Domain ↔ Entity mapping logic
 * - Repository methods delegate correctly to mapper
 * - Error handling and edge cases
 */
class PaymentOutboundAdapterTest {

    private lateinit var paymentMapper: PaymentMapper
    private lateinit var adapter: PaymentOutboundAdapter

    @BeforeEach
    fun setUp() {
        paymentMapper = mockk(relaxed = true)
        adapter = PaymentOutboundAdapter(paymentMapper)
    }

    // ==================== save Tests ====================

    @Test
    fun `save should convert domain object to entity and call mapper`() {
        // Given
        val payment = createTestPayment()
        every { paymentMapper.insert(any()) } returns 1

        // When
        adapter.save(payment)

        // Then
        verify(exactly = 1) { 
            paymentMapper.insert(
                match { entity ->
                    entity.paymentId == payment.paymentId.value &&
                    entity.publicPaymentId == payment.publicPaymentId &&
                    entity.buyerId == payment.buyerId.value &&
                    entity.orderId == payment.orderId.value &&
                    entity.amountValue == payment.totalAmount.value &&
                    entity.amountCurrency == payment.totalAmount.currency &&
                    entity.status == payment.status &&
                    entity.createdAt == payment.createdAt
                }
            )
        }
    }

    @Test
    fun `save should handle mapper exceptions`() {
        // Given
        val payment = createTestPayment()
        every { paymentMapper.insert(any()) } throws RuntimeException("Database error")

        // When/Then
        assertThrows<RuntimeException> {
            adapter.save(payment)
        }
    }

    @Test
    fun `save should handle payment with all fields populated`() {
        // Given
        val payment = createTestPaymentWithAllFields()
        every { paymentMapper.insert(any()) } returns 1

        // When
        adapter.save(payment)

        // Then
        verify(exactly = 1) { 
            paymentMapper.insert(
                match { entity ->
                    entity.paymentId == 456L &&
                    entity.publicPaymentId == "pay-456" &&
                    entity.buyerId == "buyer-456" &&
                    entity.orderId == "order-456" &&
                    entity.amountValue == 50000L &&
                    entity.amountCurrency == "EUR" &&
                    entity.status == PaymentStatus.SUCCESS &&
                    entity.createdAt == LocalDateTime.of(2023, 6, 15, 10, 30)
                }
            )
        }
    }

    // ==================== getMaxPaymentId Tests ====================

    @Test
    fun `getMaxPaymentId should return max ID from mapper`() {
        // Given
        every { paymentMapper.getMaxPaymentId() } returns 1000L

        // When
        val maxId = adapter.getMaxPaymentId()

        // Then
        assertEquals(PaymentId(1000L), maxId)
        verify(exactly = 1) { paymentMapper.getMaxPaymentId() }
    }

    @Test
    fun `getMaxPaymentId should return zero when mapper returns null`() {
        // Given
        every { paymentMapper.getMaxPaymentId() } returns null

        // When
        val maxId = adapter.getMaxPaymentId()

        // Then
        assertEquals(PaymentId(0L), maxId)
    }

    @Test
    fun `getMaxPaymentId should handle mapper exceptions`() {
        // Given
        every { paymentMapper.getMaxPaymentId() } throws RuntimeException("Database error")

        // When/Then
        assertThrows<RuntimeException> {
            adapter.getMaxPaymentId()
        }
    }

    // ==================== Domain ↔ Entity Mapping Tests ====================

    @Test
    fun `should correctly map domain object to entity for save`() {
        // Given
        val payment = createTestPaymentWithComplexData()
        every { paymentMapper.insert(any()) } returns 1

        // When
        adapter.save(payment)

        // Then
        verify(exactly = 1) { 
            paymentMapper.insert(
                match { entity ->
                    // Verify all fields are mapped correctly
                    entity.paymentId == payment.paymentId.value &&
                    entity.publicPaymentId == payment.publicPaymentId &&
                    entity.buyerId == payment.buyerId.value &&
                    entity.orderId == payment.orderId.value &&
                    entity.amountValue == payment.totalAmount.value &&
                    entity.amountCurrency == payment.totalAmount.currency &&
                    entity.status == payment.status &&
                    entity.createdAt == payment.createdAt
                }
            )
        }
    }

    @Test
    fun `should handle different payment statuses correctly`() {
        // Given
        val statuses = listOf(
            PaymentStatus.INITIATED,
            PaymentStatus.SUCCESS,
            PaymentStatus.FAILED
        )
        every { paymentMapper.insert(any()) } returns 1

        // When/Then
        statuses.forEach { status ->
            val payment = createTestPayment(status = status)
            adapter.save(payment)
            
            verify { 
                paymentMapper.insert(
                    match { entity -> entity.status == status }
                )
            }
        }
    }

    @Test
    fun `should handle different currencies correctly`() {
        // Given
        val currencies = listOf("USD", "EUR", "GBP", "JPY", "CAD")
        every { paymentMapper.insert(any()) } returns 1

        // When/Then
        currencies.forEach { currency ->
            val payment = Payment.Builder()
                .paymentId(PaymentId(123L))
                .publicPaymentId("pay-123")
                .buyerId(BuyerId("buyer-123"))
                .orderId(OrderId("order-123"))
                .totalAmount(Amount(10000L, currency))
                .status(PaymentStatus.INITIATED)
                .createdAt(LocalDateTime.now())
                .paymentOrders(emptyList())
                .build()
            adapter.save(payment)
            
            verify { 
                paymentMapper.insert(
                    match { entity -> 
                        entity.amountCurrency == currency &&
                        entity.amountValue == 10000L
                    }
                )
            }
        }
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    fun `should handle payment with zero amount`() {
        // Given
        val payment = Payment.Builder()
            .paymentId(PaymentId(123L))
            .publicPaymentId("pay-123")
            .buyerId(BuyerId("buyer-123"))
            .orderId(OrderId("order-123"))
            .totalAmount(Amount(0L, "USD"))
            .status(PaymentStatus.INITIATED)
            .createdAt(LocalDateTime.now())
            .paymentOrders(emptyList())
            .build()
        every { paymentMapper.insert(any()) } returns 1

        // When
        adapter.save(payment)

        // Then
        verify(exactly = 1) { 
            paymentMapper.insert(
                match { entity ->
                    entity.amountValue == 0L &&
                    entity.amountCurrency == "USD"
                }
            )
        }
    }

    @Test
    fun `should handle payment with large amount`() {
        // Given
        val largeAmount = Long.MAX_VALUE
        val payment = Payment.Builder()
            .paymentId(PaymentId(123L))
            .publicPaymentId("pay-123")
            .buyerId(BuyerId("buyer-123"))
            .orderId(OrderId("order-123"))
            .totalAmount(Amount(largeAmount, "USD"))
            .status(PaymentStatus.INITIATED)
            .createdAt(LocalDateTime.now())
            .paymentOrders(emptyList())
            .build()
        every { paymentMapper.insert(any()) } returns 1

        // When
        adapter.save(payment)

        // Then
        verify(exactly = 1) { 
            paymentMapper.insert(
                match { entity ->
                    entity.amountValue == largeAmount
                }
            )
        }
    }

    @Test
    fun `should handle payment with special characters in IDs`() {
        // Given
        val payment = Payment.Builder()
            .paymentId(PaymentId(123L))
            .publicPaymentId("pay-123-test_@#$%")
            .buyerId(BuyerId("buyer-456-special!@#"))
            .orderId(OrderId("order-789-unicode-测试"))
            .totalAmount(Amount(10000L, "USD"))
            .status(PaymentStatus.INITIATED)
            .createdAt(LocalDateTime.now())
            .paymentOrders(emptyList())
            .build()
        every { paymentMapper.insert(any()) } returns 1

        // When
        adapter.save(payment)

        // Then
        verify(exactly = 1) { 
            paymentMapper.insert(
                match { entity ->
                    entity.publicPaymentId == "pay-123-test_@#$%" &&
                    entity.buyerId == "buyer-456-special!@#" &&
                    entity.orderId == "order-789-unicode-测试"
                }
            )
        }
    }

    // ==================== Multiple Operations Tests ====================

    @Test
    fun `should handle multiple save operations`() {
        // Given
        val payments = (1L..5L).map { createTestPayment(id = it) }
        every { paymentMapper.insert(any()) } returns 1

        // When
        payments.forEach { adapter.save(it) }

        // Then
        verify(exactly = 5) { paymentMapper.insert(any()) }
    }

    @Test
    fun `should handle multiple getMaxPaymentId calls`() {
        // Given
        every { paymentMapper.getMaxPaymentId() } returns 100L

        // When
        val result1 = adapter.getMaxPaymentId()
        val result2 = adapter.getMaxPaymentId()

        // Then
        assertEquals(PaymentId(100L), result1)
        assertEquals(PaymentId(100L), result2)
        verify(exactly = 2) { paymentMapper.getMaxPaymentId() }
    }

    // ==================== Helper Methods ====================

    private fun createTestPayment(
        id: Long = 123L,
        status: PaymentStatus = PaymentStatus.INITIATED
    ): Payment {
        return Payment.Builder()
            .paymentId(PaymentId(id))
            .publicPaymentId("pay-$id")
            .buyerId(BuyerId("buyer-$id"))
            .orderId(OrderId("order-$id"))
            .totalAmount(Amount(10000L, "USD"))
            .status(status)
            .createdAt(LocalDateTime.now())
            .paymentOrders(emptyList())
            .build()
    }

    private fun createTestPaymentWithAllFields(): Payment {
        return Payment.Builder()
            .paymentId(PaymentId(456L))
            .publicPaymentId("pay-456")
            .buyerId(BuyerId("buyer-456"))
            .orderId(OrderId("order-456"))
            .totalAmount(Amount(50000L, "EUR"))
            .status(PaymentStatus.SUCCESS)
            .createdAt(LocalDateTime.of(2023, 6, 15, 10, 30))
            .paymentOrders(emptyList())
            .build()
    }

    private fun createTestPaymentWithComplexData(): Payment {
        return Payment.Builder()
            .paymentId(PaymentId(789L))
            .publicPaymentId("pay-789-complex")
            .buyerId(BuyerId("buyer-789-special"))
            .orderId(OrderId("order-789-unicode-测试"))
            .totalAmount(Amount(999999L, "JPY"))
            .status(PaymentStatus.INITIATED)
            .createdAt(LocalDateTime.of(2023, 12, 31, 23, 59, 59))
            .paymentOrders(emptyList())
            .build()
    }
}