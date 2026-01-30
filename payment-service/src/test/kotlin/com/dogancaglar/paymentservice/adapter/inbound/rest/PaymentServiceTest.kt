package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.application.util.toPublicPaymentIntentId
import com.dogancaglar.paymentservice.application.validator.PaymentValidator
import com.dogancaglar.paymentservice.ports.inbound.CreatePaymentIntentUseCase
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.CreatePaymentIntentRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.AuthorizationRequestDTO
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentMethodDTO
import com.dogancaglar.port.out.web.dto.AmountDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum
import com.dogancaglar.paymentservice.adapter.inbound.rest.dto.PaymentOrderLineDTO
import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.PaymentIntent
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import com.dogancaglar.paymentservice.ports.inbound.AuthorizePaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.inbound.GetPaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.inbound.UpdatePaymentIntentUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PaymentServiceTest {

    private lateinit var createPaymentIntentUseCase: CreatePaymentIntentUseCase
    private lateinit var updatePaymentIntentUseCase: UpdatePaymentIntentUseCase
    private lateinit var authorizePaymentIntentUseCase: AuthorizePaymentIntentUseCase
    private lateinit var getPaymentIntentUseCase: GetPaymentIntentUseCase
    private lateinit var paymentService: PaymentService
    private lateinit var paymentValidator: PaymentValidator

    @BeforeEach
    fun setUp() {
        createPaymentIntentUseCase = mockk()
        updatePaymentIntentUseCase = mockk()
        authorizePaymentIntentUseCase = mockk()
        getPaymentIntentUseCase = mockk()
        paymentValidator = mockk(relaxed = true)
        paymentService = PaymentService(
            authorizePaymentIntentUseCase,
            createPaymentIntentUseCase,
            getPaymentIntentUseCase,
            updatePaymentIntentUseCase,
            paymentValidator
        )
    }

    @Test
    fun `should create payment intent successfully`() {
        // Given
        val request = CreatePaymentIntentRequestDTO(
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

        val expectedPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(123L),
            buyerId = BuyerId("buyer-456"),
            orderId = OrderId("order-123"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(10000L, Currency("USD"))
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")

        every { createPaymentIntentUseCase.create(any()) } returns expectedPaymentIntent

        // When
        val response = paymentService.createPaymentIntent(request)

        // Then
        assertNotNull(response)
        assertEquals(expectedPaymentIntent.paymentIntentId.toPublicPaymentIntentId(), response.paymentIntentId)
        assertEquals("CREATED", response.status)
        assertEquals("buyer-456", response.buyerId)
        assertEquals("order-123", response.orderId)
        assertEquals(10000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.USD, response.totalAmount.currency)
        assertNotNull(response.createdAt)

        verify(exactly = 1) { paymentValidator.validate(request) }
        verify(exactly = 1) { createPaymentIntentUseCase.create(any()) }
    }

    @Test
    fun `should create payment intent with multiple payment orders`() {
        // Given
        val request = CreatePaymentIntentRequestDTO(
            orderId = "order-456",
            buyerId = "buyer-789",
            totalAmount = AmountDto(20000L, CurrencyEnum.EUR),
            paymentOrders = listOf(
                PaymentOrderLineDTO(
                    sellerId = "seller-1",
                    amount = AmountDto(15000L, CurrencyEnum.EUR)
                ),
                PaymentOrderLineDTO(
                    sellerId = "seller-2",
                    amount = AmountDto(5000L, CurrencyEnum.EUR)
                )
            )
        )

        val expectedPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(456L),
            buyerId = BuyerId("buyer-789"),
            orderId = OrderId("order-456"),
            totalAmount = Amount.of(20000L, Currency("EUR")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-1"),
                    amount = Amount.of(15000L, Currency("EUR"))
                ),
                PaymentOrderLine(
                    sellerId = SellerId("seller-2"),
                    amount = Amount.of(5000L, Currency("EUR"))
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")

        every { createPaymentIntentUseCase.create(any()) } returns expectedPaymentIntent

        // When
        val response = paymentService.createPaymentIntent(request)

        // Then
        assertNotNull(response)
        assertEquals(expectedPaymentIntent.paymentIntentId.toPublicPaymentIntentId(), response.paymentIntentId)
        assertEquals("CREATED", response.status)
        assertEquals(20000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.EUR, response.totalAmount.currency)

        verify(exactly = 1) { paymentValidator.validate(request) }
        verify(exactly = 1) { createPaymentIntentUseCase.create(any()) }
    }

    @Test
    fun `should authorize payment intent successfully`() {
        // Given
        val publicPaymentIntentId = "pi_test123"
        val request = AuthorizationRequestDTO(
            paymentMethod = PaymentMethodDTO.CardToken(
                token = "token-abc-123",
                cvc = "123"
            )
        )

        val createdPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(123L),
            buyerId = BuyerId("buyer-456"),
            orderId = OrderId("order-123"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(10000L, Currency("USD"))
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")

        val authorizedPaymentIntent = createdPaymentIntent
            .markAuthorizedPending()
            .markAuthorized()

        every { authorizePaymentIntentUseCase.authorize(any()) } returns authorizedPaymentIntent

        // When
        val response = paymentService.authorizePayment(publicPaymentIntentId, request)

        // Then
        assertNotNull(response)
        assertEquals(authorizedPaymentIntent.paymentIntentId.toPublicPaymentIntentId(), response.paymentIntentId)
        assertEquals("AUTHORIZED", response.status)
        assertEquals("buyer-456", response.buyerId)
        assertEquals("order-123", response.orderId)
        assertEquals(10000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.USD, response.totalAmount.currency)

        verify(exactly = 1) { authorizePaymentIntentUseCase.authorize(any()) }
    }

    @Test
    fun `should handle authorization that results in PENDING_AUTH status`() {
        // Given
        val publicPaymentIntentId = "pi_test456"
        val request = AuthorizationRequestDTO(
            paymentMethod = PaymentMethodDTO.CardToken(
                token = "token-xyz-789",
                cvc = "456"
            )
        )

        val createdPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(456L),
            buyerId = BuyerId("buyer-789"),
            orderId = OrderId("order-456"),
            totalAmount = Amount.of(5000L, Currency("EUR")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-1"),
                    amount = Amount.of(5000L, Currency("EUR"))
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")

        val pendingPaymentIntent = createdPaymentIntent.markAuthorizedPending()

        every { authorizePaymentIntentUseCase.authorize(any()) } returns pendingPaymentIntent

        // When
        val response = paymentService.authorizePayment(publicPaymentIntentId, request)

        // Then
        assertNotNull(response)
        assertEquals(pendingPaymentIntent.paymentIntentId.toPublicPaymentIntentId(), response.paymentIntentId)
        assertEquals("PENDING_AUTH", response.status)

        verify(exactly = 1) { authorizePaymentIntentUseCase.authorize(any()) }
    }

    @Test
    fun `should handle authorization that results in DECLINED status`() {
        // Given
        val publicPaymentIntentId = "pi_test789"
        val request = AuthorizationRequestDTO(
            paymentMethod = PaymentMethodDTO.CardToken(
                token = "token-declined",
                cvc = "999"
            )
        )

        val createdPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(789L),
            buyerId = BuyerId("buyer-999"),
            orderId = OrderId("order-789"),
            totalAmount = Amount.of(15000L, Currency("GBP")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-2"),
                    amount = Amount.of(15000L, Currency("GBP"))
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE")

        val declinedPaymentIntent = createdPaymentIntent
            .markAuthorizedPending()
            .markDeclined()

        every { authorizePaymentIntentUseCase.authorize(any()) } returns declinedPaymentIntent

        // When
        val response = paymentService.authorizePayment(publicPaymentIntentId, request)

        // Then
        assertNotNull(response)
        assertEquals(declinedPaymentIntent.paymentIntentId.toPublicPaymentIntentId(), response.paymentIntentId)
        assertEquals("DECLINED", response.status)
        assertEquals(15000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.GBP, response.totalAmount.currency)

        verify(exactly = 1) { authorizePaymentIntentUseCase.authorize(any()) }
    }

    @Test
    fun `should validate request before creating payment intent`() {
        // Given
        val request = CreatePaymentIntentRequestDTO(
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

        val expectedPaymentIntent = PaymentIntent.createNew(
            paymentIntentId = PaymentIntentId(123L),
            buyerId = BuyerId("buyer-456"),
            orderId = OrderId("order-123"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(10000L, Currency("USD"))
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("ST_PI_1234","SECRET_FROM_STRIPE") // After Stripe call succeeds, status becomes CREATED

        every { createPaymentIntentUseCase.create(any()) } returns expectedPaymentIntent

        // When
        paymentService.createPaymentIntent(request)

        // Then
        verify(exactly = 1) { paymentValidator.validate(request) }
        verify(exactly = 1) { createPaymentIntentUseCase.create(any()) }
    }

    @Test
    fun `should get payment intent successfully with clientSecret`() {
        // Given
        val publicPaymentIntentId = "pi_test123"
        val paymentIntentId = PaymentIntentId(123L)

        val paymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = BuyerId("buyer-456"),
            orderId = OrderId("order-123"),
            totalAmount = Amount.of(10000L, Currency("USD")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-789"),
                    amount = Amount.of(10000L, Currency("USD"))
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("pi_stripe_123", "secret_from_stripe")

        val paymentIntentWithClientSecret = paymentIntent.withClientSecret("retrieved_secret")

        every { getPaymentIntentUseCase.getPaymentIntent(any()) } returns paymentIntentWithClientSecret

        // When
        val response = paymentService.getPaymentIntent(publicPaymentIntentId)

        // Then
        assertNotNull(response)
        assertEquals(paymentIntentWithClientSecret.paymentIntentId.toPublicPaymentIntentId(), response.paymentIntentId)
        assertEquals("CREATED", response.status)
        assertEquals("buyer-456", response.buyerId)
        assertEquals("order-123", response.orderId)
        assertEquals(10000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.USD, response.totalAmount.currency)
        assertEquals("retrieved_secret", response.clientSecret)

        verify(exactly = 1) { getPaymentIntentUseCase.getPaymentIntent(any()) }
    }

    @Test
    fun `should get payment intent without pspReference`() {
        // Given
        val publicPaymentIntentId = "pi_test456"
        val paymentIntentId = PaymentIntentId(456L)

        val paymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = BuyerId("buyer-789"),
            orderId = OrderId("order-456"),
            totalAmount = Amount.of(5000L, Currency("EUR")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-1"),
                    amount = Amount.of(5000L, Currency("EUR"))
                )
            )
        )

        every { getPaymentIntentUseCase.getPaymentIntent(any()) } returns paymentIntent

        // When
        val response = paymentService.getPaymentIntent(publicPaymentIntentId)

        // Then
        assertNotNull(response)
        assertEquals(paymentIntent.paymentIntentId.toPublicPaymentIntentId(), response.paymentIntentId)
        assertEquals("CREATED_PENDING", response.status)
        assertEquals("buyer-789", response.buyerId)
        assertEquals("order-456", response.orderId)
        assertEquals(5000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.EUR, response.totalAmount.currency)
        assertNull(paymentIntent.pspReference)

        verify(exactly = 1) { getPaymentIntentUseCase.getPaymentIntent(any()) }
    }

    @Test
    fun `should get payment intent with AUTHORIZED status`() {
        // Given
        val publicPaymentIntentId = "pi_test789"
        val paymentIntentId = PaymentIntentId(789L)

        val paymentIntent = PaymentIntent.createNew(
            paymentIntentId = paymentIntentId,
            buyerId = BuyerId("buyer-999"),
            orderId = OrderId("order-789"),
            totalAmount = Amount.of(15000L, Currency("GBP")),
            paymentOrderLines = listOf(
                PaymentOrderLine(
                    sellerId = SellerId("seller-2"),
                    amount = Amount.of(15000L, Currency("GBP"))
                )
            )
        ).markAsCreatedWithPspReferenceAndClientSecret("pi_stripe_789", "secret")
            .markAuthorizedPending()
            .markAuthorized()

        val paymentIntentWithClientSecret = paymentIntent.withClientSecret("retrieved_secret_789")

        every { getPaymentIntentUseCase.getPaymentIntent(any()) } returns paymentIntentWithClientSecret

        // When
        val response = paymentService.getPaymentIntent(publicPaymentIntentId)

        // Then
        assertNotNull(response)
        assertEquals(paymentIntentWithClientSecret.paymentIntentId.toPublicPaymentIntentId(), response.paymentIntentId)
        assertEquals("AUTHORIZED", response.status)
        assertEquals("buyer-999", response.buyerId)
        assertEquals("order-789", response.orderId)
        assertEquals(15000L, response.totalAmount.quantity)
        assertEquals(CurrencyEnum.GBP, response.totalAmount.currency)
        assertEquals("retrieved_secret_789", response.clientSecret)

        verify(exactly = 1) { getPaymentIntentUseCase.getPaymentIntent(any()) }
    }
}