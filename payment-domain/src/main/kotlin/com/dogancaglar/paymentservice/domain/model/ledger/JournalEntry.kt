package com.dogancaglar.paymentservice.domain.model.ledger

import java.math.BigDecimal

/**
 * Represents one atomic, balanced accounting event.
 * Each JournalEntry must sum to zero across all postings.
 */
data class JournalEntry(
    val id: String,                      // e.g. "CAPTURE:A999"
    val txType: JournalType,             // e.g. AUTH_HOLD, CAPTURE, REFUND
    val name: String,                    // Human-readable label (optional)
    val postings: List<Posting>,
    val referenceType: String? = null,   // e.g. "authorization", "capture"
    val referenceId: String? = null      // e.g. "A999"
) {
    init {
        require(postings.isNotEmpty()) { "JournalEntry must have at least one posting" }
        val totalDebitAmount = postings.filterIsInstance<Posting.Debit>().sumOf { it.amount.value }
        val totalCreditAmount = postings.filterIsInstance<Posting.Credit>().sumOf { it.amount.value }
        val total = totalDebitAmount - totalCreditAmount
        require(totalDebitAmount == totalCreditAmount) {
            "Unbalanced journal entry: debits and credits differ (sum=$total)"
        }
    }
}