package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentResponseDTO
import com.dogancaglar.paymentservice.application.service.*
import com.dogancaglar.port.out.web.dto.AmountDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentOrderLineDTO
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.http.HttpStatus

class PaymentControllerTest {

    private lateinit var paymentApiOrchestrator: PaymentApiOrchestrator
    private lateinit var idempotencyService: IdempotencyService
    private lateinit var paymentController: PaymentController

    @BeforeEach
    fun setUp() {
        paymentApiOrchestrator = mockk()
        idempotencyService = mockk()
        paymentController = PaymentController(paymentApiOrchestrator, idempotencyService)
    }

    private fun createSampleRequest() = CreatePaymentIntentRequestDTO(
        orderId = "order-123",
        buyerId = "buyer-456",
        totalAmount = AmountDto(10000L, CurrencyEnum.USD),
        paymentOrders = listOf(
            PaymentOrderLineDTO(
                sellerId = "seller-789",
                amount = AmountDto(10000L, CurrencyEnum.USD)
            )
        )
    )

    private fun createSampleResponse() = CreatePaymentIntentResponseDTO(
        paymentIntentId = "payment-123",
        orderId = "order-123",
        buyerId = "buyer-456",
        totalAmount = AmountDto(10000L, CurrencyEnum.USD),
        status = "CREATED",
        createdAt = "2023-01-01T10:00:00"
    )

    @Test
    fun `should create payment successfully on first request with idempotency key`() {
        // Given
        val idempotencyKey = "idem-key-12345"
        val request = createSampleRequest()
        val expectedResponse = createSampleResponse()
        
        every {
            idempotencyService.run(idempotencyKey, request, CreatePaymentIntentResponseDTO::class.java, any(), any())
        } answers {
            val block = lastArg<() -> CreatePaymentIntentResponseDTO>()
            val result = block()
            IdempotencyResult(
                response = result,
                status = IdempotencyExecutionStatus.CREATED
            )
        }
        every { paymentApiOrchestrator.createPaymentIntent(request) } returns expectedResponse

        // When
        val result = paymentController.createPayment(idempotencyKey, request)

        // Then
        assertEquals(HttpStatus.CREATED, result.statusCode)
        assertEquals(expectedResponse, result.body)
        assertNotNull(result.headers["Location"])
        assertTrue(result.headers["Location"]!!.first().contains("payment-123"))
        verify(exactly = 1) { paymentApiOrchestrator.createPaymentIntent(request) }
        verify(exactly = 1) {
            idempotencyService.run(idempotencyKey, request, CreatePaymentIntentResponseDTO::class.java, any(), any())
        }
    }

    @Test
    fun `should return 200 OK when replaying request with same idempotency key`() {
        // Given
        val idempotencyKey = "idem-key-12345"
        val request = createSampleRequest()
        val cachedResponse = createSampleResponse()
        
        every {
            idempotencyService.run(
                idempotencyKey,
                request,
                CreatePaymentIntentResponseDTO::class.java,
                any(),
                any()
            )
        } returns IdempotencyResult(
            response = cachedResponse,
            status = IdempotencyExecutionStatus.REPLAYED
        )

        // When
        val result = paymentController.createPayment(idempotencyKey, request)

        // Then
        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(cachedResponse, result.body)
        assertNotNull(result.headers["Location"])
        verify(exactly = 0) { paymentApiOrchestrator.createPaymentIntent(any()) }
        verify(exactly = 1) {
            idempotencyService.run(idempotencyKey, request, CreatePaymentIntentResponseDTO::class.java, any(), any())
        }
    }

    @Test
    fun `should throw exception when idempotency key is missing`() {
        // Given
        val request = createSampleRequest()

        // When & Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            paymentController.createPayment(null, request)
        }
        assertEquals("Idempotency-Key header is required", exception.message)
        verify(exactly = 0) { paymentApiOrchestrator.createPaymentIntent(any()) }
        verify(exactly = 0) { idempotencyService.run<Any, Any>(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should throw exception when idempotency key is blank`() {
        // Given
        val request = createSampleRequest()

        // When & Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            paymentController.createPayment("   ", request)
        }
        assertEquals("Idempotency-Key header is required", exception.message)
        verify(exactly = 0) { paymentApiOrchestrator.createPaymentIntent(any()) }
        verify(exactly = 0) { idempotencyService.run<Any, CreatePaymentIntentResponseDTO>(any(), any(), CreatePaymentIntentResponseDTO::class.java, any(), any()) }
    }

    @Test
    fun `should throw IdempotencyConflictException when same key used with different payload`() {
        // Given
        val idempotencyKey = "idem-key-12345"
        val request = createSampleRequest()
        val differentRequest = request.copy(orderId = "different-order-456")
        
        every {
            idempotencyService.run(
                idempotencyKey,
                differentRequest,
                CreatePaymentIntentResponseDTO::class.java,
                any(),
                any()
            )
        } throws IdempotencyConflictClientException("Idempotency-Key reused with different payload")

        // When & Then
        assertThrows(IdempotencyConflictClientException::class.java) {
            paymentController.createPayment(idempotencyKey, differentRequest)
        }
        verify(exactly = 0) { paymentApiOrchestrator.createPaymentIntent(any()) }
        verify(exactly = 1) {
            idempotencyService.run(idempotencyKey, differentRequest, CreatePaymentIntentResponseDTO::class.java, any(), any())
        }
    }

    @Test
    fun `should propagate exception from payment service when payment creation fails`() {
        // Given
        // SCENARIO: First request where paymentApiOrchestrator.createPayment() throws an exception
        //
        // ACTUAL FLOW (with fix):
        // 1. Controller calls idempotencyService.run(key, request) { paymentApiOrchestrator.createPayment(request) }
        // 2. IdempotencyService.run():
        //    a. Hashes request body
        //    b. Calls store.tryInsertPending() -> creates PENDING record (returns true for first request)
        //    c. Executes block() which calls paymentApiOrchestrator.createPayment(request)
        //    d. When block() throws exception:
        //       - Catches exception in try-catch
        //       - Calls store.deletePending() to cleanup the PENDING record
        //       - Re-throws exception to controller
        // 3. Controller doesn't catch exception -> propagates to Spring exception handler
        //
        // RESULT: HTTP 500 error, idempotency record is DELETED
        //
        // NOTE: If client retries with same key:
        //   - Retry creates a new PENDING record (previous one was cleaned up)
        //   - Payment operation is retried (not waiting for completion)
        
        val idempotencyKey = "idem-key-12345"
        val request = createSampleRequest()
        val serviceException = RuntimeException("Payment creation failed")
        
        // When idempotencyService.run() is called, it will execute the block
        // The block throws, so exception propagates (after cleanup)
        every {
            idempotencyService.run(idempotencyKey, request, CreatePaymentIntentResponseDTO::class.java, any(), any())
        } throws serviceException

        // When & Then - Exception should propagate through idempotency layer to controller
        assertThrows(RuntimeException::class.java) {
            paymentController.createPayment(idempotencyKey, request)
        }
        
        // Verify idempotency service was invoked
        verify(exactly = 1) {
            idempotencyService.run(idempotencyKey, request, CreatePaymentIntentResponseDTO::class.java, any(), any())
        }
    }

    @Test
    fun `should include Location header in response`() {
        // Given
        val idempotencyKey = "idem-key-12345"
        val request = createSampleRequest()
        val expectedResponse = createSampleResponse()
        
        every {
            idempotencyService.run(idempotencyKey, request, CreatePaymentIntentResponseDTO::class.java, any(), any())
        } answers {
            val block = lastArg<() -> CreatePaymentIntentResponseDTO>()
            val result = block()
            IdempotencyResult(
                response = result,
                status = IdempotencyExecutionStatus.CREATED
            )
        }
        every { paymentApiOrchestrator.createPaymentIntent(request) } returns expectedResponse

        // When
        val result = paymentController.createPayment(idempotencyKey, request)

        // Then
        assertNotNull(result.headers["Location"])
        assertEquals("/api/v1/payments/payment-123", result.headers["Location"]!!.first())
    }

    @Test
    fun `should handle different idempotency keys as separate requests`() {
        // Given
        val idempotencyKey1 = "idem-key-111"
        val idempotencyKey2 = "idem-key-222"
        val request = createSampleRequest()
        val response1 = createSampleResponse()
        val response2 = createSampleResponse().copy(paymentIntentId = "payment-456")
        
        every {
            idempotencyService.run(idempotencyKey1, request, CreatePaymentIntentResponseDTO::class.java, any(), any())
        } answers {
            val block = lastArg<() -> CreatePaymentIntentResponseDTO>()
            val result = block()
            IdempotencyResult(
                response = result,
                status = IdempotencyExecutionStatus.CREATED
            )
        }
        every {
            idempotencyService.run(idempotencyKey2, request, CreatePaymentIntentResponseDTO::class.java, any(), any())
        } answers {
            val block = lastArg<() -> CreatePaymentIntentResponseDTO>()
            val result = block()
            IdempotencyResult(
                response = result,
                status = IdempotencyExecutionStatus.CREATED
            )
        }
        every { paymentApiOrchestrator.createPaymentIntent(request) } returnsMany listOf(response1, response2)

        // When
        val result1 = paymentController.createPayment(idempotencyKey1, request)
        val result2 = paymentController.createPayment(idempotencyKey2, request)

        // Then
        assertEquals(HttpStatus.CREATED, result1.statusCode)
        assertEquals(HttpStatus.CREATED, result2.statusCode)
        assertEquals("payment-123", result1.body?.paymentIntentId)
        assertEquals("payment-456", result2.body?.paymentIntentId)
        verify(exactly = 2) { paymentApiOrchestrator.createPaymentIntent(request) }
    }
}