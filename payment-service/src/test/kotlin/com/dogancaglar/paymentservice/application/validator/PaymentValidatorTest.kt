package com.dogancaglar.paymentservice.application.validator

import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.AccountCategory
import com.dogancaglar.paymentservice.domain.model.ledger.AccountProfile
import com.dogancaglar.paymentservice.domain.model.ledger.AccountStatus
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.port.out.web.dto.AmountDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum
import com.dogancaglar.port.out.web.dto.PaymentOrderRequestDTO
import com.dogancaglar.port.out.web.dto.PaymentRequestDTO
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PaymentValidatorTest {

    private lateinit var accountDirectory: AccountDirectoryPort
    private lateinit var validator: PaymentValidator

    @BeforeEach
    fun setUp() {
        accountDirectory = mockk(relaxed = true)
        validator = PaymentValidator(accountDirectory)
    }

    @Test
    fun `validate should pass for valid payment request`() {
        // Given
        val request = PaymentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 20000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderRequestDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR)),
                PaymentOrderRequestDTO(sellerId = "seller-2", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR))
            )
        )

        every { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-1") } returns AccountProfile(
            accountCode = "MERCHANT_PAYABLE.seller-1.EUR",
            type = AccountType.MERCHANT_PAYABLE,
            entityId = "seller-1",
            currency = Currency("EUR"),
            category = AccountCategory.LIABILITY,
            country = "NL",
            status = AccountStatus.ACTIVE
        )
        every { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-2") } returns AccountProfile(
            accountCode = "MERCHANT_PAYABLE.seller-2.EUR",
            type = AccountType.MERCHANT_PAYABLE,
            entityId = "seller-2",
            currency = Currency("EUR"),
            category = AccountCategory.LIABILITY,
            country = "NL",
            status = AccountStatus.ACTIVE
        )

        // When/Then - Should not throw
        assertDoesNotThrow { validator.validate(request) }
    }

    @Test
    fun `validate should throw when duplicate seller IDs exist`() {
        // Given
        val request = PaymentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 20000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderRequestDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR)),
                PaymentOrderRequestDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR))
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
        val request = PaymentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 30000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderRequestDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR)),
                PaymentOrderRequestDTO(sellerId = "seller-2", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR))
            )
        )

        every { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, any()) } returns AccountProfile(
            accountCode = "MERCHANT_PAYABLE.test.EUR",
            type = AccountType.MERCHANT_PAYABLE,
            entityId = "test",
            currency = Currency("EUR"),
            category = AccountCategory.LIABILITY,
            country = "NL",
            status = AccountStatus.ACTIVE
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
        val request = PaymentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderRequestDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.USD))
            )
        )

        // When/Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            validator.validate(request)
        }
        assertNotNull(exception.message)
        // The error message should mention the currency mismatch
        assertTrue(exception.message!!.contains("USD") || exception.message!!.contains("EUR"), 
            "Exception message should contain currency: ${exception.message}")
        assertTrue(exception.message!!.contains("currency") || exception.message!!.contains("PaymentOrder"), 
            "Exception message should mention currency: ${exception.message}")
    }

    @Test
    fun `validate should throw when seller account currency differs from payment currency`() {
        // Given
        val request = PaymentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderRequestDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR))
            )
        )

        every { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-1") } returns AccountProfile(
            accountCode = "MERCHANT_PAYABLE.seller-1.USD",
            type = AccountType.MERCHANT_PAYABLE,
            entityId = "seller-1",
            currency = Currency("USD"),
            category = AccountCategory.LIABILITY,
            country = "US",
            status = AccountStatus.ACTIVE
        )

        // When/Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            validator.validate(request)
        }
        assertNotNull(exception.message)
        // The error message should mention the currency mismatch
        assertTrue(exception.message!!.contains("USD") || exception.message!!.contains("EUR"), 
            "Exception message should contain currency: ${exception.message}")
        assertTrue(exception.message!!.contains("currency") || exception.message!!.contains("settles") || exception.message!!.contains("payment"), 
            "Exception message should mention currency mismatch: ${exception.message}")
    }

    @Test
    fun `validate should throw when seller account is not active`() {
        // Given
        val request = PaymentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderRequestDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR))
            )
        )

        every { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-1") } returns AccountProfile(
            accountCode = "MERCHANT_PAYABLE.seller-1.EUR",
            type = AccountType.MERCHANT_PAYABLE,
            entityId = "seller-1",
            currency = Currency("EUR"),
            category = AccountCategory.LIABILITY,
            country = "NL",
            status = AccountStatus.SUSPENDED
        )

        // When/Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            validator.validate(request)
        }
        assertTrue(exception.message?.contains("is not active") == true)
    }

    @Test
    fun `validate should throw when seller account is closed`() {
        // Given
        val request = PaymentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderRequestDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR))
            )
        )

        every { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-1") } returns AccountProfile(
            accountCode = "MERCHANT_PAYABLE.seller-1.EUR",
            type = AccountType.MERCHANT_PAYABLE,
            entityId = "seller-1",
            currency = Currency("EUR"),
            category = AccountCategory.LIABILITY,
            country = "NL",
            status = AccountStatus.CLOSED
        )

        // When/Then
        val exception = assertThrows(IllegalArgumentException::class.java) {
            validator.validate(request)
        }
        assertTrue(exception.message?.contains("is not active") == true)
    }

    @Test
    fun `validate should validate all payment orders for multiple sellers`() {
        // Given
        val request = PaymentRequestDTO(
            buyerId = "buyer-123",
            totalAmount = AmountDto(quantity = 30000L, currency = CurrencyEnum.EUR),
            orderId = "ORDER-123",
            paymentOrders = listOf(
                PaymentOrderRequestDTO(sellerId = "seller-1", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR)),
                PaymentOrderRequestDTO(sellerId = "seller-2", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR)),
                PaymentOrderRequestDTO(sellerId = "seller-3", amount = AmountDto(quantity = 10000L, currency = CurrencyEnum.EUR))
            )
        )

        every { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-1") } returns AccountProfile(
            accountCode = "MERCHANT_PAYABLE.seller-1.EUR",
            type = AccountType.MERCHANT_PAYABLE,
            entityId = "seller-1",
            currency = Currency("EUR"),
            category = AccountCategory.LIABILITY,
            country = "NL",
            status = AccountStatus.ACTIVE
        )
        every { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-2") } returns AccountProfile(
            accountCode = "MERCHANT_PAYABLE.seller-2.EUR",
            type = AccountType.MERCHANT_PAYABLE,
            entityId = "seller-2",
            currency = Currency("EUR"),
            category = AccountCategory.LIABILITY,
            country = "NL",
            status = AccountStatus.ACTIVE
        )
        every { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-3") } returns AccountProfile(
            accountCode = "MERCHANT_PAYABLE.seller-3.EUR",
            type = AccountType.MERCHANT_PAYABLE,
            entityId = "seller-3",
            currency = Currency("EUR"),
            category = AccountCategory.LIABILITY,
            country = "NL",
            status = AccountStatus.ACTIVE
        )

        // When/Then - Should not throw
        assertDoesNotThrow { validator.validate(request) }

        // Verify all sellers were checked
        verify(exactly = 1) { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-1") }
        verify(exactly = 1) { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-2") }
        verify(exactly = 1) { accountDirectory.getAccountProfile(AccountType.MERCHANT_PAYABLE, "seller-3") }
    }
}

