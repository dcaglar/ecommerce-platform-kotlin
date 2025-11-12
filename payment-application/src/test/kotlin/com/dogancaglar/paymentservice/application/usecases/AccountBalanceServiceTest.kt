package com.dogancaglar.paymentservice.application.usecases

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.balance.AccountBalanceSnapshot
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import com.dogancaglar.paymentservice.util.LedgerEntryTestHelper
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountBalanceServiceTest {

    private lateinit var snapshotPort: AccountBalanceSnapshotPort
    private lateinit var cachePort: AccountBalanceCachePort
    private lateinit var service: AccountBalanceService

    // ───────────────────────────────────────────────
    // Constants for readability
    // ───────────────────────────────────────────────
    private val EUR_05 = 500L
    private val EUR_10 = 1000L
    private val EUR_20 = 2000L
    private val EUR_30 = 3000L
    private val EUR_35 = 3500L

    private val SELLER_A = "SELLER-A"
    private val SELLER_B = "SELLER-B"
    private val SELLER_X = "SELLER-X"
    private val SELLER_Z = "SELLER-Z"

    @BeforeEach
    fun setUp() {
        snapshotPort = mockk(relaxed = true)
        cachePort = mockk(relaxed = true)
        service = AccountBalanceService(snapshotPort, cachePort)
    }

    // ───────────────────────────────────────────────
    // 1️⃣  Empty list → no updates
    // ───────────────────────────────────────────────
    @Test
    fun `should do nothing when no ledger entries provided`() {
        val result = service.updateAccountBalancesBatch(ledgerEntries = emptyList())

        assertTrue(result.isEmpty())
        verify { snapshotPort wasNot Called }
        verify { cachePort wasNot Called }
    }

    // ───────────────────────────────────────────────
    // 2️⃣  Single capture → 4 postings updated
    // ───────────────────────────────────────────────
    @Test
    fun `should update balances for all accounts in a single ledger entry`() {
        // Given
        val entry = LedgerEntryTestHelper.createCaptureLedgerEntry(
            10L,
            "PO-10",
            SELLER_A,
            Amount.of(EUR_10, Currency("EUR"))
        )

        val accountCodes = entry.journalEntry.postings.map { it.account.accountCode }.toSet()
        every { snapshotPort.findByAccountCodes(accountCodes = accountCodes) } returns emptyList()

        // When
        val result = service.updateAccountBalancesBatch(ledgerEntries = listOf(entry))

        // Then
        assertEquals(listOf(10L), result)

        verify {
            cachePort.addDeltaAndWatermark(accountCode = "AUTH_RECEIVABLE.GLOBAL.EUR", delta = -EUR_10, upToEntryId= 10L)
            cachePort.addDeltaAndWatermark(accountCode = "AUTH_LIABILITY.GLOBAL.EUR", delta = -EUR_10, upToEntryId= 10L)
            cachePort.addDeltaAndWatermark(accountCode = "MERCHANT_PAYABLE.$SELLER_A.EUR", delta = +EUR_10, upToEntryId= 10L)
            cachePort.addDeltaAndWatermark(accountCode = "PSP_RECEIVABLES.GLOBAL.EUR", delta = +EUR_10, upToEntryId= 10L)

            cachePort.markDirty(accountCode = "AUTH_RECEIVABLE.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "AUTH_LIABILITY.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "MERCHANT_PAYABLE.$SELLER_A.EUR")
            cachePort.markDirty(accountCode = "PSP_RECEIVABLES.GLOBAL.EUR")
        }

        confirmVerified(cachePort)
    }

    // ───────────────────────────────────────────────
    // 3️⃣  Two captures same seller → aggregate
    // ───────────────────────────────────────────────
    @Test
    fun `should aggregate deltas and use max ledgerEntryId`() {
        val entry1 = LedgerEntryTestHelper.createCaptureLedgerEntry(
            5L,
            "PO-5",
            SELLER_B,
            Amount.of(EUR_10, Currency("EUR"))
        )
        val entry2 = LedgerEntryTestHelper.createCaptureLedgerEntry(
            7L,
            "PO-7",
            SELLER_B,
            Amount.of(EUR_20, Currency("EUR"))
        )

        val accountCodes = (entry1.journalEntry.postings + entry2.journalEntry.postings)
            .map { it.account.accountCode }.toSet()
        every { snapshotPort.findByAccountCodes(accountCodes = accountCodes) } returns emptyList()

        // When
        val result = service.updateAccountBalancesBatch(ledgerEntries = listOf(entry1, entry2))

        // Then
        assertEquals(listOf(7L), result)

        verify {
            cachePort.addDeltaAndWatermark(accountCode = "AUTH_RECEIVABLE.GLOBAL.EUR", delta = -(EUR_10 + EUR_20), upToEntryId= 7L)
            cachePort.addDeltaAndWatermark(accountCode = "AUTH_LIABILITY.GLOBAL.EUR", delta = -(EUR_10 + EUR_20), upToEntryId= 7L)
            cachePort.addDeltaAndWatermark(accountCode = "MERCHANT_PAYABLE.$SELLER_B.EUR", delta = +(EUR_10 + EUR_20), upToEntryId= 7L)
            cachePort.addDeltaAndWatermark(accountCode = "PSP_RECEIVABLES.GLOBAL.EUR", delta = +(EUR_10 + EUR_20), upToEntryId= 7L)

            cachePort.markDirty(accountCode = "AUTH_RECEIVABLE.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "AUTH_LIABILITY.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "MERCHANT_PAYABLE.$SELLER_B.EUR")
            cachePort.markDirty(accountCode = "PSP_RECEIVABLES.GLOBAL.EUR")
        }

        confirmVerified(cachePort)
    }

    // ───────────────────────────────────────────────
    // 4️⃣  Watermark skip
    // ───────────────────────────────────────────────
    @Test
    fun `should skip postings already applied based on watermark`() {
        val entry = LedgerEntryTestHelper.createCaptureLedgerEntry(
            15L,
            "PO-15",
            SELLER_X,
            Amount.of(EUR_10, Currency("EUR"))
        )

        val accountCodes = entry.journalEntry.postings.map { it.account.accountCode }.toSet()
        val existingSnapshots = accountCodes.map {
            AccountBalanceSnapshot(
                accountCode = it,
                balance = 0L,
                lastAppliedEntryId = 20L,
                lastSnapshotAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        }
        every { snapshotPort.findByAccountCodes(accountCodes = accountCodes) } returns existingSnapshots

        val result = service.updateAccountBalancesBatch(ledgerEntries = listOf(entry))

        assertTrue(result.isEmpty())
        verify(exactly = 0) { cachePort.addDeltaAndWatermark(accountCode = any(), delta = any(), upToEntryId= any()) }
        verify(exactly = 0) { cachePort.markDirty(accountCode = any()) }
    }

    // ───────────────────────────────────────────────
    // 5️⃣  Auth+Capture pair → only non-zero accounts
    // ───────────────────────────────────────────────
    @Test
    fun `should update only non-zero accounts in authHold plus capture flow`() {
        val authEntry = LedgerEntryTestHelper.createAuthHoldLedgerEntry(
            1L,
            "1235",
            Amount.of(EUR_10, Currency("EUR"))
        )

        val captureEntry = LedgerEntryTestHelper.createCaptureLedgerEntry(
            2L,
            "PO-ZERO",
            SELLER_Z,
            Amount.of(EUR_10, Currency("EUR"))
        )

        val entries = listOf(authEntry,captureEntry)

        val accountCodes = entries.flatMap { it.journalEntry.postings }.map { it.account.accountCode }.toSet()
        every { snapshotPort.findByAccountCodes(accountCodes = accountCodes) } returns emptyList()

        val result = service.updateAccountBalancesBatch(ledgerEntries = entries)
        assertEquals(listOf(captureEntry.ledgerEntryId), result)

        verify {
            cachePort.addDeltaAndWatermark(accountCode = "MERCHANT_PAYABLE.$SELLER_Z.EUR", delta = +EUR_10, upToEntryId= captureEntry.ledgerEntryId)
            cachePort.addDeltaAndWatermark(accountCode = "PSP_RECEIVABLES.GLOBAL.EUR", delta = +EUR_10, upToEntryId= captureEntry.ledgerEntryId)
            cachePort.markDirty(accountCode = "MERCHANT_PAYABLE.$SELLER_Z.EUR")
            cachePort.markDirty(accountCode = "PSP_RECEIVABLES.GLOBAL.EUR")
        }

        verify(exactly = 0) {
            cachePort.addDeltaAndWatermark(accountCode = "AUTH_RECEIVABLE.GLOBAL.EUR", delta = any(), upToEntryId= any())
            cachePort.addDeltaAndWatermark(accountCode = "AUTH_LIABILITY.GLOBAL.EUR", delta = any(), upToEntryId= any())
            cachePort.markDirty(accountCode = "AUTH_RECEIVABLE.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "AUTH_LIABILITY.GLOBAL.EUR")
        }

        confirmVerified(cachePort)
    }

    // ───────────────────────────────────────────────
    // 6️⃣  Multi-seller → independent aggregation
    // ───────────────────────────────────────────────
    @Test
    fun `should aggregate independently for multiple sellers`() {
        val entryA1 = LedgerEntryTestHelper.createCaptureLedgerEntry(
            30L,
            "PO-30",
            SELLER_A,
            Amount.of(EUR_10, Currency("EUR"))
        )
        val entryA2 = LedgerEntryTestHelper.createCaptureLedgerEntry(
            35L,
            "PO-35",
            SELLER_A,
            Amount.of(EUR_20, Currency("EUR"))
        )
        val entryB = LedgerEntryTestHelper.createCaptureLedgerEntry(
            40L,
            "PO-40",
            SELLER_B,
            Amount.of(EUR_05, Currency("EUR"))
        )

        val allEntries = listOf(entryA1, entryA2, entryB)
        val accountCodes = allEntries.flatMap { it.journalEntry.postings.map { p -> p.account.accountCode } }.toSet()
        every { snapshotPort.findByAccountCodes(accountCodes = accountCodes) } returns emptyList()

        val result = service.updateAccountBalancesBatch(ledgerEntries = allEntries)
        assertEquals(setOf(35L, 40L), result.toSet())

        verify {
            cachePort.addDeltaAndWatermark(accountCode = "AUTH_RECEIVABLE.GLOBAL.EUR", delta = -EUR_35, upToEntryId= 40L)
            cachePort.addDeltaAndWatermark(accountCode = "AUTH_LIABILITY.GLOBAL.EUR", delta = -EUR_35, upToEntryId= 40L)
            cachePort.addDeltaAndWatermark(accountCode = "PSP_RECEIVABLES.GLOBAL.EUR", delta = +EUR_35, upToEntryId= 40L)

            cachePort.addDeltaAndWatermark(accountCode = "MERCHANT_PAYABLE.$SELLER_A.EUR", delta = +(EUR_10 + EUR_20), upToEntryId= 35L)
            cachePort.addDeltaAndWatermark(accountCode = "MERCHANT_PAYABLE.$SELLER_B.EUR", delta = +EUR_05, upToEntryId= 40L)

            cachePort.markDirty(accountCode = "AUTH_RECEIVABLE.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "AUTH_LIABILITY.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "PSP_RECEIVABLES.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "MERCHANT_PAYABLE.$SELLER_A.EUR")
            cachePort.markDirty(accountCode = "MERCHANT_PAYABLE.$SELLER_B.EUR")
        }

        confirmVerified(cachePort)
    }

    // ───────────────────────────────────────────────
    // 7️⃣  Mixed upToEntryId→ skip Seller A, apply Seller B + globals
    // ───────────────────────────────────────────────
    @Test
    fun `should skip sellers below upToEntryIdbut aggregate globals across all`() {
        val entryA1 = LedgerEntryTestHelper.createCaptureLedgerEntry(
            30L,
            "PO-30",
            SELLER_A,
            Amount.of(EUR_10, Currency("EUR"))
        )
        val entryA2 = LedgerEntryTestHelper.createCaptureLedgerEntry(
            35L,
            "PO-35",
            SELLER_A,
            Amount.of(EUR_20, Currency("EUR"))
        )
        val entryB = LedgerEntryTestHelper.createCaptureLedgerEntry(
            40L,
            "PO-40",
            SELLER_B,
            Amount.of(EUR_05, Currency("EUR"))
        )

        val entries = listOf(entryA1, entryA2, entryB)
        val allCodes = entries.flatMap { it.journalEntry.postings.map { it.account.accountCode } }.toSet()

        val snapshots = allCodes.map { code ->
            when {
                code.contains(SELLER_A) -> AccountBalanceSnapshot(
                    accountCode = code, balance = 0L, lastAppliedEntryId = 35L,
                    lastSnapshotAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
                )
                else -> AccountBalanceSnapshot(
                    accountCode = code, balance = 0L, lastAppliedEntryId = 0L,
                    lastSnapshotAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()
                )
            }
        }
        every { snapshotPort.findByAccountCodes(accountCodes = allCodes) } returns snapshots

        val result = service.updateAccountBalancesBatch(ledgerEntries = entries)

        assertEquals(listOf(40L), result)
        verify {
            cachePort.addDeltaAndWatermark(accountCode = "AUTH_RECEIVABLE.GLOBAL.EUR", delta = -EUR_35, upToEntryId= 40L)
            cachePort.addDeltaAndWatermark(accountCode = "AUTH_LIABILITY.GLOBAL.EUR", delta = -EUR_35, upToEntryId= 40L)
            cachePort.addDeltaAndWatermark(accountCode = "PSP_RECEIVABLES.GLOBAL.EUR", delta = +EUR_35, upToEntryId= 40L)
            cachePort.addDeltaAndWatermark(accountCode = "MERCHANT_PAYABLE.$SELLER_B.EUR", delta = +EUR_05, upToEntryId= 40L)

            cachePort.markDirty(accountCode = "AUTH_RECEIVABLE.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "AUTH_LIABILITY.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "PSP_RECEIVABLES.GLOBAL.EUR")
            cachePort.markDirty(accountCode = "MERCHANT_PAYABLE.$SELLER_B.EUR")
        }

        verify(exactly = 0) {
            cachePort.addDeltaAndWatermark(accountCode = "MERCHANT_PAYABLE.$SELLER_A.EUR", delta = any(), upToEntryId= any())
            cachePort.markDirty(accountCode = "MERCHANT_PAYABLE.$SELLER_A.EUR")
        }

        confirmVerified(cachePort)
    }
}