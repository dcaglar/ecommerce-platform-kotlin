package com.dogancaglar.paymentservice.domain.model.ledger

data class LedgerOperationResult(
    val transaction: PaymentTx,
    val journalEntries: List<JournalEntry>
)
