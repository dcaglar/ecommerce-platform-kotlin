package com.dogancaglar.paymentservice.adapter.outbound.persistance.entity

import java.time.LocalDateTime

data class LedgerEntryEntity(
    val id: Long? = null,
    val journalId: String,
    val accountId: String,
    val accountType: String,
    val amount: Long,
    val direction: String, // "DEBIT" or "CREDIT"
    val currency: String,
    val createdAt: LocalDateTime
)