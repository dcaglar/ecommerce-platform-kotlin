package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.application.model.LedgerEntry

interface LedgerEntryPort {
    fun postLedgerEntriesAtomic(entries: List<LedgerEntry>)
}