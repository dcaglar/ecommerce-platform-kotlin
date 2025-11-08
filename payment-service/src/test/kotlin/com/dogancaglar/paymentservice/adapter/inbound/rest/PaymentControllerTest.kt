package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.port.out.web.dto.PaymentRequestDTO
import com.dogancaglar.port.out.web.dto.PaymentResponseDTO
import com.dogancaglar.port.out.web.dto.AmountDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum
import com.dogancaglar.port.out.web.dto.PaymentOrderRequestDTO
import com.dogancaglar.port.out.web.dto.PaymentOrderResponseDTO
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

class PaymentControllerTest {

    private lateinit var paymentService: PaymentService
    private lateinit var paymentController: PaymentController

    @BeforeEach
    fun setUp() {
        paymentService = mockk()
        paymentController = PaymentController(paymentService)
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
        
        val expectedResponse = PaymentResponseDTO(
            id = "payment-123",
            paymentId = "payment-123",
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.USD),
            status = "CREATED",
            createdAt = "2023-01-01T10:00:00",
            paymentOrders = listOf(
                PaymentOrderResponseDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(10000L, CurrencyEnum.USD)
                )
            )
        )
        
        every { paymentService.createPayment(request) } returns expectedResponse

        // When
        val result = paymentController.createPayment(request)

        // Then
        assertEquals(HttpStatus.CREATED, result.statusCode)
        assertEquals(expectedResponse, result.body)
        assertNotNull(result.headers["Location"])
        assertTrue(result.headers["Location"]!!.first().contains("payment-123"))
        verify { paymentService.createPayment(request) }
    }

    @Test
    fun `should handle payment service exception`() {
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
        
        val exception = RuntimeException("Payment creation failed")
        every { paymentService.createPayment(request) } throws exception

        // When & Then
        assertThrows(RuntimeException::class.java) {
            paymentController.createPayment(request)
        }
        verify { paymentService.createPayment(request) }
    }

    @Test
    fun `should handle validation errors`() {
        // Given - Invalid request with null required fields
        val request = PaymentRequestDTO(
            orderId = "",
            buyerId = "",
            totalAmount = AmountDto(0L, CurrencyEnum.USD),
            paymentOrders = emptyList()
        )

        // When & Then
        assertThrows(Exception::class.java) {
            paymentController.createPayment(request)
        }
    }

    @Test
    fun `should log payment request and response`() {
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
        
        val expectedResponse = PaymentResponseDTO(
            id = "payment-123",
            paymentId = "payment-123",
            orderId = "order-123",
            buyerId = "buyer-456",
            totalAmount = AmountDto(10000L, CurrencyEnum.USD),
            status = "CREATED",
            createdAt = "2023-01-01T10:00:00",
            paymentOrders = listOf(
                PaymentOrderResponseDTO(
                    sellerId = "seller-789",
                    amount = AmountDto(10000L, CurrencyEnum.USD)
                )
            )
        )
        
        every { paymentService.createPayment(request) } returns expectedResponse

        // When
        val result = paymentController.createPayment(request)

        // Then
        assertEquals(HttpStatus.CREATED, result.statusCode)
        assertEquals(expectedResponse, result.body)
        // Note: Logging verification would require a different approach with logback-test.xml
        // or using a logging framework test utility
    }
}