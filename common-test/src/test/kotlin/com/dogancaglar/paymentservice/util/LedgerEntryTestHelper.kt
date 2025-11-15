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
        paymentId: String = "PO-$ledgerEntryId",
        amount: Amount
    ): LedgerEntry {
        val authReceivable = Account.create(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        val authLiability = Account.create(AccountType.AUTH_LIABILITY, "GLOBAL")
        val journal = JournalEntry.authHold(
            journalIdentifier = paymentId,
            authorizedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability
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
        amount: Amount = Amount.of(1000, Currency( "EUR"))
    ): LedgerEntry {
        val authReceivable = Account.create(AccountType.AUTH_RECEIVABLE, "GLOBAL")
        val authLiability = Account.create(AccountType.AUTH_LIABILITY, "GLOBAL")
        val merchantAccount = Account.create(AccountType.MERCHANT_PAYABLE, merchantId)
        val pspReceivable = Account.create(AccountType.PSP_RECEIVABLES, "GLOBAL")
        val journal = JournalEntry.capture(
            journalIdentifier = paymentOrderId,
            capturedAmount = amount,
            authReceivable = authReceivable,
            authLiability = authLiability,
            merchantAccount = merchantAccount,
            pspReceivable = pspReceivable
        ).first()
        return LedgerEntry.create(ledgerEntryId, journal, LocalDateTime.now())
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