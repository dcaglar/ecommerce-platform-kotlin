package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.application.util.toPublicPaymentId
import com.dogancaglar.paymentservice.application.validator.PaymentValidator
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentRequestDTO
import com.dogancaglar.port.out.web.dto.AmountDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentLineDTO
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PaymentServiceTest {

    private lateinit var createPaymentUseCase: CreatePaymentUseCase
    private lateinit var paymentService: PaymentService
    private lateinit var paymentValidator: PaymentValidator

    @BeforeEach
    fun setUp() {
        createPaymentUseCase = mockk()
        paymentValidator = mockk(relaxed = true)
        paymentService = PaymentService(mockk(), createPaymentUseCase, paymentValidator)
    }

    @Test
    fun `should create payment successfully`() {
        // Given
        val request = CreatePaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentLineDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(10000L, CurrencyEnum.USD)
                )
            )
        )
        
        val expectedPayment = Payment.createNew(
            paymentId = PaymentId(123L),
            buyerId = BuyerId("buyer-456"),
            orderId = OrderId("order-123"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(10000L, Currency("USD"))
                )
            )
        )
        
        every { createPaymentUseCase.create(any()) } returns expectedPayment

        // When
        val result = paymentService.createPayment(request)

        // Then
        assertNotNull(result)
        assertEquals(expectedPayment.paymentId.toPublicPaymentId(), result.paymentId)
        assertEquals("order-123", result.orderId)
        assertEquals("CREATED", result.status)
        assertEquals(10000L, result.totalAmount.quantity)
        assertEquals(CurrencyEnum.USD, result.totalAmount.currency)
        
        verify { createPaymentUseCase.create(any()) }
    }

    @Test
    fun `should handle use case exception and rethrow`() {
        // Given
        val request = CreatePaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentLineDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(10000L, CurrencyEnum.USD)
                )
            )
        )
        
        val exception = RuntimeException("Use case failed")
        every { createPaymentUseCase.create(any()) } throws exception

        // When & Then
        val thrownException = assertThrows(RuntimeException::class.java) {
            paymentService.createPayment(request)
        }
        
        assertEquals("Use case failed", thrownException.message)
        verify { createPaymentUseCase.create(any()) }
    }

    @Test
    fun `should handle validation exception`() {
        // Given
        val request = CreatePaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentLineDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(10000L, CurrencyEnum.USD)
                )
            )
        )
        
        val validationException = IllegalArgumentException("Invalid payment data")
        every { createPaymentUseCase.create(any()) } throws validationException

        // When & Then
        val thrownException = assertThrows(IllegalArgumentException::class.java) {
            paymentService.createPayment(request)
        }
        
        assertEquals("Invalid payment data", thrownException.message)
        verify { createPaymentUseCase.create(any()) }
    }

    @Test
    fun `should handle different payment statuses`() {
        // Given
        val request = CreatePaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(5000L, CurrencyEnum.EUR),
            paymentOrders = listOf(
                PaymentLineDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(5000L, CurrencyEnum.EUR)
                )
            )
        )
        
        val expectedPayment = Payment.createNew(
            paymentId = PaymentId(456L),
            buyerId = BuyerId("buyer-456"),
            orderId = OrderId("order-123"),
            totalAmount = Amount.of(5000L, Currency("EUR")),
            paymentLines = listOf(
                PaymentLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(5000L, Currency("EUR"))
                )
            )
        )
            .startAuthorization()
            .authorize()
        
        every { createPaymentUseCase.create(any()) } returns expectedPayment

        // When
        val result = paymentService.createPayment(request)

        // Then
        assertEquals(expectedPayment.paymentId.toPublicPaymentId(), result.paymentId)
        assertEquals("AUTHORIZED", result.status)
        assertEquals(5000L, result.totalAmount.quantity)
        assertEquals(CurrencyEnum.EUR, result.totalAmount.currency)
        
        verify { createPaymentUseCase.create(any()) }
    }

    @Test
    fun `should handle large amounts correctly`() {
        // Given
        val request = CreatePaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(99999999L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentLineDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(99999999L, CurrencyEnum.USD)
                )
            )
        )
        
        val expectedPayment = Payment.createNew(
            paymentId = PaymentId(789L),
            buyerId = BuyerId("buyer-456"),
            orderId = OrderId("order-123"),
            totalAmount = Amount.of(99999999L, Currency("USD")),
            paymentLines = listOf(
                PaymentLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(99999999L, Currency("USD"))
                )
            )
        )
        
        every { createPaymentUseCase.create(any()) } returns expectedPayment

        // When
        val result = paymentService.createPayment(request)

        // Then
        assertEquals(expectedPayment.paymentId.toPublicPaymentId(), result.paymentId)
        assertEquals(99999999L, result.totalAmount.quantity)
        
        verify { createPaymentUseCase.create(any()) }
    }
}