package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.PaymentRequestMapper
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentUseCase
import com.dogancaglar.port.out.web.dto.PaymentRequestDTO
import com.dogancaglar.port.out.web.dto.PaymentResponseDTO
import com.dogancaglar.port.out.web.dto.AmountDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum
import com.dogancaglar.port.out.web.dto.PaymentOrderRequestDTO
import com.dogancaglar.paymentservice.domain.model.Payment
import com.dogancaglar.paymentservice.domain.model.PaymentStatus
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PaymentServiceTest {

    private lateinit var createPaymentUseCase: CreatePaymentUseCase
    private lateinit var paymentService: PaymentService
    private lateinit var clock: Clock

    @BeforeEach
    fun setUp() {
        createPaymentUseCase = mockk()
        clock = Clock.fixed(Instant.parse("2023-01-01T10:00:00Z"), ZoneOffset.UTC)
        paymentService = PaymentService(createPaymentUseCase)
    }

    @Test
    fun `should create payment successfully`() {
        // Given
        val request = PaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentOrderRequestDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(10000L, CurrencyEnum.USD)
                )
            )
        )
        
        val expectedPayment = Payment.builder()
            .paymentId(PaymentId(123L))
            .publicPaymentId("payment-123")
            .orderId(OrderId("order-123"))
            .buyerId(BuyerId("buyer-456"))
            .totalAmount(Amount.of(10000L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .paymentOrders(listOf())
            .buildNew()
        
        every { createPaymentUseCase.create(any()) } returns expectedPayment

        // When
        val result = paymentService.createPayment(request)

        // Then
        assertNotNull(result)
        assertEquals("payment-123", result.paymentId)
        assertEquals("order-123", result.orderId)
        assertEquals("INITIATED", result.status)
        assertEquals(10000L, result.totalAmount.quantity)
        assertEquals(CurrencyEnum.USD, result.totalAmount.currency)
        
        verify { createPaymentUseCase.create(any()) }
    }

    @Test
    fun `should handle use case exception and rethrow`() {
        // Given
        val request = PaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentOrderRequestDTO(
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
        val request = PaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentOrderRequestDTO(
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
        val request = PaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(5000L, CurrencyEnum.EUR),
            paymentOrders = listOf(
                PaymentOrderRequestDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(5000L, CurrencyEnum.EUR)
                )
            )
        )
        
        val expectedPayment = Payment.builder()
            .paymentId(PaymentId(456L))
            .publicPaymentId("payment-456")
            .orderId(OrderId("order-123"))
            .buyerId(BuyerId("buyer-456"))
            .totalAmount(Amount.of(5000L, Currency("EUR")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .paymentOrders(listOf())
            .buildNew()
        
        every { createPaymentUseCase.create(any()) } returns expectedPayment

        // When
        val result = paymentService.createPayment(request)

        // Then
        assertEquals("payment-456", result.paymentId)
        assertEquals("INITIATED", result.status)
        assertEquals(5000L, result.totalAmount.quantity)
        assertEquals(CurrencyEnum.EUR, result.totalAmount.currency)
        
        verify { createPaymentUseCase.create(any()) }
    }

    @Test
    fun `should handle large amounts correctly`() {
        // Given
        val request = PaymentRequestDTO(
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(99999999L, CurrencyEnum.USD),
            paymentOrders = listOf(
                PaymentOrderRequestDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(99999999L, CurrencyEnum.USD)
                )
            )
        )
        
        val expectedPayment = Payment.builder()
            .paymentId(PaymentId(789L))
            .publicPaymentId("payment-789")
            .orderId(OrderId("order-123"))
            .buyerId(BuyerId("buyer-456"))
            .totalAmount(Amount.of(99999999L, Currency("USD")))
            .createdAt(clock.instant().atZone(clock.zone).toLocalDateTime())
            .paymentOrders(listOf())
            .buildNew()
        
        every { createPaymentUseCase.create(any()) } returns expectedPayment

        // When
        val result = paymentService.createPayment(request)

        // Then
        assertEquals("payment-789", result.paymentId)
        assertEquals(99999999L, result.totalAmount.quantity)
        
        verify { createPaymentUseCase.create(any()) }
    }
}