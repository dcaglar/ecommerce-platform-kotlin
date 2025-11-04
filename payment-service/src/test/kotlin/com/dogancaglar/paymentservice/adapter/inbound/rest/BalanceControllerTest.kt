package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.port.out.web.dto.BalanceDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class BalanceControllerTest {

    private lateinit var balanceService: BalanceService
    private lateinit var balanceController: BalanceController

    @BeforeEach
    fun setUp() {
        balanceService = mockk(relaxed = true)
        balanceController = BalanceController(balanceService)
    }

    @Test
    fun `getSellerBalance should return BalanceDto for valid seller`() {
        // Given
        val sellerId = "seller-123"
        val expectedBalance = BalanceDto(
            balance = 50000L,
            currency = CurrencyEnum.EUR,
            accountCode = "MERCHANT_ACCOUNT.seller-123.EUR",
            sellerId = sellerId
        )

        every { balanceService.getSellerBalance(sellerId) } returns expectedBalance

        // When
        val result = balanceController.getSellerBalance(sellerId)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(expectedBalance, result.body)
        assertEquals(50000L, result.body?.balance)
        assertEquals(CurrencyEnum.EUR, result.body?.currency)
        assertEquals(sellerId, result.body?.sellerId)

        verify(exactly = 1) { balanceService.getSellerBalance(sellerId) }
    }

    @Test
    fun `getSellerBalance should handle different currencies`() {
        // Given
        val sellerId = "seller-456"
        val expectedBalance = BalanceDto(
            balance = 123456L,
            currency = CurrencyEnum.USD,
            accountCode = "MERCHANT_ACCOUNT.seller-456.USD",
            sellerId = sellerId
        )

        every { balanceService.getSellerBalance(sellerId) } returns expectedBalance

        // When
        val result = balanceController.getSellerBalance(sellerId)

        // Then
        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(CurrencyEnum.USD, result.body?.currency)
        assertEquals(123456L, result.body?.balance)
    }

    @Test
    fun `getSellerBalance should handle zero balance`() {
        // Given
        val sellerId = "seller-789"
        val expectedBalance = BalanceDto(
            balance = 0L,
            currency = CurrencyEnum.EUR,
            accountCode = "MERCHANT_ACCOUNT.seller-789.EUR",
            sellerId = sellerId
        )

        every { balanceService.getSellerBalance(sellerId) } returns expectedBalance

        // When
        val result = balanceController.getSellerBalance(sellerId)

        // Then
        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(0L, result.body?.balance)
    }

    @Test
    fun `getSellerBalance should propagate exceptions from service`() {
        // Given
        val sellerId = "non-existent-seller"
        val exception = IllegalArgumentException("Account not found")

        every { balanceService.getSellerBalance(sellerId) } throws exception

        // When/Then
        val thrownException = assertThrows(IllegalArgumentException::class.java) {
            balanceController.getSellerBalance(sellerId)
        }

        assertEquals("Account not found", thrownException.message)
        verify(exactly = 1) { balanceService.getSellerBalance(sellerId) }
    }

    @Test
    fun `getMyBalance should extract sellerId from JWT and return balance`() {
        // Given
        val sellerId = "seller-me"
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("seller_id", sellerId)
            .claim("sub", "user-123")
            .build()
        val authentication = JwtAuthenticationToken(jwt)

        val expectedBalance = BalanceDto(
            balance = 75000L,
            currency = CurrencyEnum.EUR,
            accountCode = "MERCHANT_ACCOUNT.seller-me.EUR",
            sellerId = sellerId
        )

        every { balanceService.getSellerBalance(sellerId) } returns expectedBalance

        // When
        val result = balanceController.getMyBalance(authentication)

        // Then
        assertNotNull(result)
        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(expectedBalance, result.body)
        assertEquals(sellerId, result.body?.sellerId)

        verify(exactly = 1) { balanceService.getSellerBalance(sellerId) }
    }

    @Test
    fun `getMyBalance should throw when seller_id claim is missing`() {
        // Given
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", "user-123")
            // Missing seller_id claim
            .build()
        val authentication = JwtAuthenticationToken(jwt)

        // When/Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            balanceController.getMyBalance(authentication)
        }

        assertTrue(exception.message?.contains("seller_id claim not found") == true)
        verify(exactly = 0) { balanceService.getSellerBalance(any()) }
    }

    @Test
    fun `getMyBalance should throw when seller_id claim is null`() {
        // Given
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("seller_id", null)
            .claim("sub", "user-123")
            .build()
        val authentication = JwtAuthenticationToken(jwt)

        // When/Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            balanceController.getMyBalance(authentication)
        }

        assertTrue(exception.message?.contains("seller_id claim not found") == true)
    }

    @Test
    fun `getMyBalance should handle different currencies from JWT`() {
        // Given
        val sellerId = "seller-gbp"
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("seller_id", sellerId)
            .claim("sub", "user-456")
            .build()
        val authentication = JwtAuthenticationToken(jwt)

        val expectedBalance = BalanceDto(
            balance = 99999L,
            currency = CurrencyEnum.GBP,
            accountCode = "MERCHANT_ACCOUNT.seller-gbp.GBP",
            sellerId = sellerId
        )

        every { balanceService.getSellerBalance(sellerId) } returns expectedBalance

        // When
        val result = balanceController.getMyBalance(authentication)

        // Then
        assertEquals(HttpStatus.OK, result.statusCode)
        assertEquals(CurrencyEnum.GBP, result.body?.currency)
        assertEquals(99999L, result.body?.balance)
    }

    @Test
    fun `getMyBalance should propagate service exceptions`() {
        // Given
        val sellerId = "seller-error"
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("seller_id", sellerId)
            .claim("sub", "user-789")
            .build()
        val authentication = JwtAuthenticationToken(jwt)

        val exception = RuntimeException("Service error")
        every { balanceService.getSellerBalance(sellerId) } throws exception

        // When/Then
        val thrownException = assertThrows(RuntimeException::class.java) {
            balanceController.getMyBalance(authentication)
        }

        assertEquals("Service error", thrownException.message)
        verify(exactly = 1) { balanceService.getSellerBalance(sellerId) }
    }
}

