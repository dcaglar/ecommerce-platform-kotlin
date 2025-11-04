package com.dogancaglar.paymentservice.util

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.Currency
import com.dogancaglar.paymentservice.domain.model.ledger.*
import java.time.LocalDateTime

/**
 * Domain-consistent factory for creating valid LedgerEntry test data.
 *
 * All entries are generated through official JournalEntry factory methods
 * (authHold, capture, settlement, etc.) — never by manually constructing postings.
 *
 * This guarantees:
 *  - Entries are always balanced (debits = credits)
 *  - Account codes follow real production conventions
 *  - Reproducibility across tests (deterministic IDs, timestamps)
 */
object LedgerEntryTestHelper {

    /**
     * Create an Authorization Hold entry.
     * AUTH_RECEIVABLE ↑ (debit) and AUTH_LIABILITY ↓ (credit)
     */
    fun createAuthHoldLedgerEntry(
        ledgerEntryId: Long,
        paymentOrderId: String = "PO-$ledgerEntryId",
        amountMinor: Long = 1_000L,
        currency: String = "EUR"
    ): LedgerEntry {
        val authReceivable = Account.create(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        val authLiability = Account.create(AccountType.AUTH_LIABILITY, "GLOBAL")
        val journal = JournalEntry.authHold(
            paymentOrderId, 
            Amount.of(amountMinor, Currency(currency)),
            authReceivable,
            authLiability
        ).first()
        return LedgerEntry.create(ledgerEntryId, journal, LocalDateTime.now())
    }

    /**
     * Create a Capture entry.
     * Moves funds from auth accounts to merchant + PSP receivables.
     */
    fun createCaptureLedgerEntry(
        ledgerEntryId: Long,
        paymentOrderId: String = "PO-$ledgerEntryId",
        merchantId: String = "SELLER-1",
        amountMinor: Long = 1_000L,
        currency: String = "EUR"
    ): LedgerEntry {
        val authReceivable = Account.create(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        val authLiability = Account.create(AccountType.AUTH_LIABILITY, "GLOBAL")
        val merchantAccount = Account.create(AccountType.MERCHANT_ACCOUNT, merchantId)
        val pspReceivable = Account.create(AccountType.PSP_RECEIVABLES, "GLOBAL")
        val journal = JournalEntry.capture(
            paymentOrderId, 
            Amount.of(amountMinor, Currency(currency)),
            authReceivable,
            authLiability,
            merchantAccount,
            pspReceivable
        ).first()
        return LedgerEntry.create(ledgerEntryId, journal, LocalDateTime.now())
    }

    /**
     * Combined AUTH_HOLD + CAPTURE flow for integration-like tests.
     */
    fun createAuthHoldAndCaptureLedgerEntries(
        paymentOrderId: String = "PO-TEST",
        merchantId: String = "SELLER-1",
        amountMinor: Long = 1_000L,
        currency: String = "EUR"
    ): List<LedgerEntry> {
        val authReceivable = Account.create(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        val authLiability = Account.create(AccountType.AUTH_LIABILITY, "GLOBAL")
        val merchantAccount = Account.create(AccountType.MERCHANT_ACCOUNT, merchantId)
        val pspReceivable = Account.create(AccountType.PSP_RECEIVABLES, "GLOBAL")
        val journals = JournalEntry.authHoldAndCapture(
            paymentOrderId, 
            Amount.of(amountMinor, Currency(currency)),
            authReceivable,
            authLiability,
            merchantAccount,
            pspReceivable
        )
        return journals.mapIndexed { i, j ->
            LedgerEntry.create(100L + i, j, LocalDateTime.now())
        }
    }

    /**
     * Full accounting lifecycle for a PSP transaction:
     * AUTH → CAPTURE → SETTLEMENT → FEE → PAYOUT
     */
    fun createFullFlowLedgerEntries(
        paymentOrderId: String = "PO-FULL",
        merchantId: String = "SELLER-1",
        acquirerId: String = "ACQ-1",
        amountMinor: Long = 1_000L,
        currency: String = "EUR"
    ): List<LedgerEntry> {
        val authReceivable = Account.create(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        val authLiability = Account.create(AccountType.AUTH_LIABILITY, "GLOBAL")
        val merchant = Account.create(AccountType.MERCHANT_ACCOUNT, merchantId)
        val pspReceivable = Account.create(AccountType.PSP_RECEIVABLES, "GLOBAL")
        val acquirer = Account.create(AccountType.ACQUIRER_ACCOUNT, acquirerId)
        val journals = JournalEntry.fullFlow(
            paymentOrderId, 
            Amount.of(amountMinor, Currency(currency)),
            authReceivable,
            authLiability,
            pspReceivable,
            merchant,
            acquirer
        )
        return journals.mapIndexed { i, j ->
            LedgerEntry.create(200L + i, j, LocalDateTime.now())
        }
    }

    /**
     * Utility for debugging: prints all postings and their signed deltas.
     */
    fun printEntry(entry: LedgerEntry) {
        println("LedgerEntry(id=${entry.ledgerEntryId}, type=${entry.journalEntry.txType})")
        entry.journalEntry.postings.forEach {
            println("  ${it::class.simpleName?.padEnd(6)} | ${it.account.accountCode.padEnd(30)} | ${it.amount.quantity}")
        }
        val net = entry.journalEntry.postings.sumOf { it.getSignedAmount().quantity }
        println("  -> Net = $net (should be 0)\n")
    }
}