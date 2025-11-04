package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.Currency
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for Account domain model.
 * 
 * Verifies:
 * - Factory methods create accounts correctly
 * - Account code format includes type, entityId, and currency
 * - Validation of entityId (non-empty)
 * - isDebitAccount() and isCreditAccount() methods
 * - Currency handling
 */
class AccountTest {

    @Test
    fun `create should create account with correct type and entityId`() {
        val account = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-123")

        assertEquals(AccountType.MERCHANT_ACCOUNT, account.type)
        assertEquals("seller-123", account.entityId)
        assertEquals("EUR", account.currency.currencyCode) // Default currency
        assertEquals(AuthType.SALE, account.authType) // Default authType
    }

    @Test
    fun `create should default to EUR currency`() {
        val account = Account.create(AccountType.CASH, "GLOBAL")

        assertEquals(Currency("EUR"), account.currency)
        assertEquals("EUR", account.currency.currencyCode)
    }

    @Test
    fun `create should default to SALE authType`() {
        val account = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-456")

        assertEquals(AuthType.SALE, account.authType)
    }

    @Test
    fun `accountCode should follow format TYPE_ENTITY_CURRENCY`() {
        val account = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-789")

        assertEquals("MERCHANT_ACCOUNT.seller-789.EUR", account.accountCode)
    }

    @Test
    fun `accountCode should include currency code`() {
        val usdAccount = Account.mock(AccountType.CASH, "GLOBAL", "USD")
        val eurAccount = Account.mock(AccountType.CASH, "GLOBAL", "EUR")

        assertEquals("CASH.GLOBAL.USD", usdAccount.accountCode)
        assertEquals("CASH.GLOBAL.EUR", eurAccount.accountCode)
    }

    @Test
    fun `create should reject empty entityId`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Account.create(AccountType.MERCHANT_ACCOUNT, "")
        }
        assertTrue(exception.message?.contains("Entity id cant be empty") == true)
    }

    @Test
    fun `create should reject blank entityId`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Account.create(AccountType.CASH, "   ")
        }
        assertTrue(exception.message?.contains("Entity id cant be empty") == true)
    }

    @Test
    fun `fromProfile should create account with profile properties`() {
        val profile = AccountProfile(
            accountCode = "MERCHANT_ACCOUNT.seller-999.EUR",
            type = AccountType.MERCHANT_ACCOUNT,
            entityId = "seller-999",
            currency = Currency("EUR"),
            category = AccountCategory.LIABILITY,
            country = "US",
            status = AccountStatus.ACTIVE
        )

        val account = Account.fromProfile(profile)

        assertEquals(AccountType.MERCHANT_ACCOUNT, account.type)
        assertEquals("seller-999", account.entityId)
        assertEquals(Currency("EUR"), account.currency)
        assertEquals("MERCHANT_ACCOUNT.seller-999.EUR", account.accountCode)
    }

    @Test
    fun `fromProfile should handle different currencies`() {
        val profile = AccountProfile(
            accountCode = "CASH.GLOBAL.USD",
            type = AccountType.CASH,
            entityId = "GLOBAL",
            currency = Currency("USD"),
            category = AccountCategory.ASSET,
            country = null,
            status = AccountStatus.ACTIVE
        )

        val account = Account.fromProfile(profile)

        assertEquals(Currency("USD"), account.currency)
        assertEquals("CASH.GLOBAL.USD", account.accountCode)
    }

    @Test
    fun `mock should create account with test defaults`() {
        val account = Account.mock(AccountType.PSP_RECEIVABLES)

        assertEquals(AccountType.PSP_RECEIVABLES, account.type)
        assertEquals("GLOBAL", account.entityId)
        assertEquals("EUR", account.currency.currencyCode)
    }

    @Test
    fun `mock should allow custom entityId`() {
        val account = Account.mock(AccountType.MERCHANT_ACCOUNT, "custom-seller")

        assertEquals("custom-seller", account.entityId)
        assertEquals("MERCHANT_ACCOUNT.custom-seller.EUR", account.accountCode)
    }

    @Test
    fun `mock should allow custom currency`() {
        val account = Account.mock(AccountType.CASH, "GLOBAL", "GBP")

        assertEquals("GBP", account.currency.currencyCode)
        assertEquals("CASH.GLOBAL.GBP", account.accountCode)
    }

    @Test
    fun `isDebitAccount should return true for debit account types`() {
        val cashAccount = Account.create(AccountType.CASH, "GLOBAL")
        val pspReceivableAccount = Account.create(AccountType.PSP_RECEIVABLES, "GLOBAL")
        val authReceivableAccount = Account.create(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        val schemeFeesAccount = Account.create(AccountType.SCHEME_FEES, "GLOBAL")

        assertTrue(cashAccount.isDebitAccount())
        assertTrue(pspReceivableAccount.isDebitAccount())
        assertTrue(authReceivableAccount.isDebitAccount())
        assertTrue(schemeFeesAccount.isDebitAccount())
    }

    @Test
    fun `isDebitAccount should return false for credit account types`() {
        val merchantAccount = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-1")
        val authLiabilityAccount = Account.create(AccountType.AUTH_LIABILITY, "GLOBAL")
        val processingFeeAccount = Account.create(AccountType.PROCESSING_FEE_REVENUE, "GLOBAL")

        assertFalse(merchantAccount.isDebitAccount())
        assertFalse(authLiabilityAccount.isDebitAccount())
        assertFalse(processingFeeAccount.isDebitAccount())
    }

    @Test
    fun `isCreditAccount should return true for credit account types`() {
        val merchantAccount = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-2")
        val authLiabilityAccount = Account.create(AccountType.AUTH_LIABILITY, "GLOBAL")
        val processingFeeAccount = Account.create(AccountType.PROCESSING_FEE_REVENUE, "GLOBAL")

        assertTrue(merchantAccount.isCreditAccount())
        assertTrue(authLiabilityAccount.isCreditAccount())
        assertTrue(processingFeeAccount.isCreditAccount())
    }

    @Test
    fun `isCreditAccount should return false for debit account types`() {
        val cashAccount = Account.create(AccountType.CASH, "GLOBAL")
        val pspReceivableAccount = Account.create(AccountType.PSP_RECEIVABLES, "GLOBAL")
        val authReceivableAccount = Account.create(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        val schemeFeesAccount = Account.create(AccountType.SCHEME_FEES, "GLOBAL")

        assertFalse(cashAccount.isCreditAccount())
        assertFalse(pspReceivableAccount.isCreditAccount())
        assertFalse(authReceivableAccount.isCreditAccount())
        assertFalse(schemeFeesAccount.isCreditAccount())
    }

    @Test
    fun `isDebitAccount and isCreditAccount should be mutually exclusive`() {
        val allAccountTypes = AccountType.values()

        allAccountTypes.forEach { accountType ->
            val account = Account.create(accountType, "test-entity")
            val isDebit = account.isDebitAccount()
            val isCredit = account.isCreditAccount()

            assertTrue(isDebit != isCredit, 
                "Account type ${accountType} should be either debit OR credit, but was both or neither")
        }
    }

    @Test
    fun `accounts with same type entityId and currency should have same accountCode`() {
        val account1 = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-123")
        val account2 = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-123")

        assertEquals(account1.accountCode, account2.accountCode)
    }

    @Test
    fun `accounts with different currencies should have different accountCodes`() {
        val usdAccount = Account.mock(AccountType.MERCHANT_ACCOUNT, "seller-123", "USD")
        val eurAccount = Account.mock(AccountType.MERCHANT_ACCOUNT, "seller-123", "EUR")

        assertNotEquals(usdAccount.accountCode, eurAccount.accountCode)
        assertEquals("MERCHANT_ACCOUNT.seller-123.USD", usdAccount.accountCode)
        assertEquals("MERCHANT_ACCOUNT.seller-123.EUR", eurAccount.accountCode)
    }

    @Test
    fun `accounts with different entityIds should have different accountCodes`() {
        val account1 = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-1")
        val account2 = Account.create(AccountType.MERCHANT_ACCOUNT, "seller-2")

        assertNotEquals(account1.accountCode, account2.accountCode)
    }

    @Test
    fun `account should handle various entityId formats`() {
        val formats = listOf(
            "GLOBAL",
            "seller-123",
            "MERCHANT-456",
            "entity_with_underscores",
            "12345",
            "entity-with-dashes"
        )

        formats.forEach { entityId ->
            val account = Account.create(AccountType.CASH, entityId)
            assertEquals(entityId, account.entityId)
            assertTrue(account.accountCode.contains(entityId))
        }
    }

    @Test
    fun `all account types should have valid normalBalance`() {
        AccountType.values().forEach { accountType ->
            val account = Account.create(accountType, "test")
            assertTrue(account.isDebitAccount() || account.isCreditAccount(),
                "Account type ${accountType} must have either DEBIT or CREDIT normal balance")
        }
    }

    @Test
    fun `all account types should have valid category`() {
        AccountType.values().forEach { accountType ->
            val account = Account.create(accountType, "test")
            assertNotNull(account.type.category, 
                "Account type ${accountType} must have a valid category")
        }
    }
}

