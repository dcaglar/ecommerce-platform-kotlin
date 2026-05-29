package com.dogancaglar.paymentservice.application.validator

import com.dogancaglar.port.out.web.dto.AmountDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentOrderLineDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PaymentValidatorTest {

    private lateinit var validator: PaymentValidator

    @BeforeEach
    fun setUp() {
        validator = PaymentValidator()
    }

    @Test
    fun `validate should pass for valid payment request`() {
        // Given
        val request = CreatePaymentIntentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 20000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderLineDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR)),
                PaymentOrderLineDTO(sellerId = "seller-2", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR))
            )
        )

        // When/Then - Should not throw
        assertDoesNotThrow { validator.validate(request) }
    }

    @Test
    fun `validate should throw when duplicate seller IDs exist`() {
        // Given
        val request = CreatePaymentIntentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 20000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderLineDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR)),
                PaymentOrderLineDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR))
            )
        )

        // When/Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            validator.validate(request)
        }
        assertTrue(exception.message?.contains("Duplicate seller IDs") == true)
    }

    @Test
    fun `validate should throw when sum of payment orders does not equal total amount`() {
        // Given
        val request = CreatePaymentIntentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 30000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderLineDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR)),
                PaymentOrderLineDTO(sellerId = "seller-2", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR))
            )
        )

        // When/Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            validator.validate(request)
        }
        assertTrue(exception.message?.contains("Sum of payment order amounts") == true)
        assertTrue(exception.message?.contains("20000") == true)
        assertTrue(exception.message?.contains("30000") == true)
    }

    @Test
    fun `validate should throw when payment order currency differs from total amount currency`() {
        // Given
        val request = CreatePaymentIntentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderLineDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.USD))
            )
        )

        // When/Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            validator.validate(request)
        }
        assertNotNull(exception.message)
        assertTrue(exception.message!!.contains("USD") || exception.message!!.contains("EUR"))
    }
}
