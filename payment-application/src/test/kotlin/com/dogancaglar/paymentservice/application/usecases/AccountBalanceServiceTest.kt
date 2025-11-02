package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.event.LedgerEntryEventData
import com.dogancaglar.paymentservice.domain.event.PostingDirection
import com.dogancaglar.paymentservice.domain.event.PostingEventData
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.ledger.JournalType
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.BalanceIdempotencyPort
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AccountBalanceServiceTest {

    private lateinit var balanceIdempotencyPort: BalanceIdempotencyPort
    private lateinit var accountBalanceCachePort: AccountBalanceCachePort
    private lateinit var service: AccountBalanceService

    @BeforeEach
    fun setUp() {
        balanceIdempotencyPort = mockk(relaxed = true)
        accountBalanceCachePort = mockk(relaxed = true)
        service = AccountBalanceService(balanceIdempotencyPort, accountBalanceCachePort)
    }

    @Test
    fun `updateAccountBalancesBatch should skip processing when already processed`() {
        // Given
        val ledgerEntries = listOf(
            createLedgerEntryEventData(1001L, "MERCHANT_ACCOUNT.MERCHANT-456", AccountType.MERCHANT_ACCOUNT, 10000L)
        )
        every { balanceIdempotencyPort.areLedgerEntryIdsProcessed(listOf(1001L)) } returns true

        // When
        val result = service.updateAccountBalancesBatch(ledgerEntries)

        // Then
        assertEquals(emptyList<Long>(), result)
        verify(exactly = 1) { balanceIdempotencyPort.areLedgerEntryIdsProcessed(listOf(1001L)) }
        verify(exactly = 0) { accountBalanceCachePort.incrementDelta(any(), any()) }
        verify(exactly = 0) { balanceIdempotencyPort.markLedgerEntryIdsProcessed(any()) }
    }

    @Test
    fun `updateAccountBalancesBatch should process new entries and update Redis deltas`() {
        // Given
        val ledgerEntries = listOf(
            createLedgerEntryEventData(
                ledgerEntryId = 1001L,
                accountCode = "MERCHANT_ACCOUNT.MERCHANT-456",
                accountType = AccountType.MERCHANT_ACCOUNT,
                amount = 10000L
            )
        )
        every { balanceIdempotencyPort.areLedgerEntryIdsProcessed(listOf(1001L)) } returns false

        // When
        val result = service.updateAccountBalancesBatch(ledgerEntries)

        // Then
        assertEquals(listOf(1001L), result)
        
        // Verify idempotency check
        verify(exactly = 1) { balanceIdempotencyPort.areLedgerEntryIdsProcessed(listOf(1001L)) }
        
        // Verify Redis delta update (MERCHANT_ACCOUNT is credit, CREDIT posting = +amount)
        verify(exactly = 1) {
            accountBalanceCachePort.incrementDelta(
                "MERCHANT_ACCOUNT.MERCHANT-456",
                10000L
            )
        }
        
        // Verify IDs marked as processed
        verify(exactly = 1) { balanceIdempotencyPort.markLedgerEntryIdsProcessed(listOf(1001L)) }
    }

    @Test
    fun `updateAccountBalancesBatch should calculate signed amounts correctly for debit accounts`() {
        // Given - AUTH_RECEIVABLE is a debit account
        val ledgerEntries = listOf(
            createLedgerEntryEventData(
                ledgerEntryId = 1001L,
                accountCode = "AUTH_RECEIVABLE.GLOBAL",
                accountType = AccountType.AUTH_RECEIVABLE,
                amount = 5000L,
                direction = PostingDirection.DEBIT
            )
        )
        
        every { balanceIdempotencyPort.areLedgerEntryIdsProcessed(listOf(1001L)) } returns false

        // When
        service.updateAccountBalancesBatch(ledgerEntries)

        // Then - DEBIT account + DEBIT posting = +amount
        verify(exactly = 1) {
            accountBalanceCachePort.incrementDelta(
                "AUTH_RECEIVABLE.GLOBAL",
                5000L
            )
        }
    }

    @Test
    fun `updateAccountBalancesBatch should calculate signed amounts correctly for credit accounts`() {
        // Given - MERCHANT_ACCOUNT is a credit account
        val ledgerEntries = listOf(
            createLedgerEntryEventData(
                ledgerEntryId = 1001L,
                accountCode = "MERCHANT_ACCOUNT.MERCHANT-456",
                accountType = AccountType.MERCHANT_ACCOUNT,
                amount = 8000L,
                direction = PostingDirection.CREDIT
            )
        )
        
        every { balanceIdempotencyPort.areLedgerEntryIdsProcessed(listOf(1001L)) } returns false

        // When
        service.updateAccountBalancesBatch(ledgerEntries)

        // Then - CREDIT account + CREDIT posting = +amount
        verify(exactly = 1) {
            accountBalanceCachePort.incrementDelta(
                "MERCHANT_ACCOUNT.MERCHANT-456",
                8000L
            )
        }
    }

    @Test
    fun `updateAccountBalancesBatch should handle negative deltas correctly`() {
        // Given - DEBIT account with CREDIT posting (reduces balance)
        val ledgerEntries = listOf(
            createLedgerEntryEventData(
                ledgerEntryId = 1001L,
                accountCode = "AUTH_RECEIVABLE.GLOBAL",
                accountType = AccountType.AUTH_RECEIVABLE,
                amount = 3000L,
                direction = PostingDirection.CREDIT  // Credit reduces debit account
            )
        )
        
        every { balanceIdempotencyPort.areLedgerEntryIdsProcessed(listOf(1001L)) } returns false

        // When
        service.updateAccountBalancesBatch(ledgerEntries)

        // Then - DEBIT account + CREDIT posting = -amount
        verify(exactly = 1) {
            accountBalanceCachePort.incrementDelta(
                "AUTH_RECEIVABLE.GLOBAL",
                -3000L
            )
        }
    }

    @Test
    fun `updateAccountBalancesBatch should aggregate multiple postings for same account`() {
        // Given - Multiple ledger entries with postings to same account
        val ledgerEntries = listOf(
            createLedgerEntryEventData(
                ledgerEntryId = 1001L,
                accountCode = "MERCHANT_ACCOUNT.MERCHANT-456",
                accountType = AccountType.MERCHANT_ACCOUNT,
                amount = 10000L,
                direction = PostingDirection.CREDIT
            ),
            createLedgerEntryEventData(
                ledgerEntryId = 1002L,
                accountCode = "MERCHANT_ACCOUNT.MERCHANT-456",
                accountType = AccountType.MERCHANT_ACCOUNT,
                amount = 5000L,
                direction = PostingDirection.CREDIT
            )
        )
        
        every { balanceIdempotencyPort.areLedgerEntryIdsProcessed(listOf(1001L, 1002L)) } returns false

        // When
        service.updateAccountBalancesBatch(ledgerEntries)

        // Then - Should aggregate: 10000 + 5000 = 15000
        verify(exactly = 1) {
            accountBalanceCachePort.incrementDelta(
                "MERCHANT_ACCOUNT.MERCHANT-456",
                15000L
            )
        }
    }

    @Test
    fun `updateAccountBalancesBatch should return empty list for empty input`() {
        // When
        val result = service.updateAccountBalancesBatch(emptyList())

        // Then
        assertEquals(emptyList<Long>(), result)
        verify(exactly = 0) { balanceIdempotencyPort.areLedgerEntryIdsProcessed(any()) }
        verify(exactly = 0) { accountBalanceCachePort.incrementDelta(any(), any()) }
    }

    @Test
    fun `updateAccountBalancesBatch should build account ID with correct format`() {
        // Given
        val ledgerEntries = listOf(
            createLedgerEntryEventData(
                ledgerEntryId = 1001L,
                accountCode = "CASH.GLOBAL",
                accountType = AccountType.CASH,
                amount = 20000L
            )
        )
        
        every { balanceIdempotencyPort.areLedgerEntryIdsProcessed(listOf(1001L)) } returns false

        // When
        service.updateAccountBalancesBatch(ledgerEntries)

        // Then - Use accountCode directly
        verify(exactly = 1) {
            accountBalanceCachePort.incrementDelta(
                "CASH.GLOBAL",
                any()
            )
        }
    }

    // Helper function to create test data
    private fun createLedgerEntryEventData(
        ledgerEntryId: Long,
        accountCode: String,
        accountType: AccountType,
        amount: Long,
        currency: String = "USD",
        direction: PostingDirection = PostingDirection.CREDIT
    ): LedgerEntryEventData {
        return LedgerEntryEventData.create(
            ledgerEntryId = ledgerEntryId,
            journalEntryId = "JOURNAL:$ledgerEntryId",
            journalType = JournalType.CAPTURE,
            journalName = "Test Journal",
            createdAt = LocalDateTime.now(),
            postings = listOf(
                PostingEventData.create(
                    accountCode = accountCode,
                    accountType = accountType,
                    amount = amount,
                    currency = currency,
                    direction = direction
                )
            )
        )
    }
}

