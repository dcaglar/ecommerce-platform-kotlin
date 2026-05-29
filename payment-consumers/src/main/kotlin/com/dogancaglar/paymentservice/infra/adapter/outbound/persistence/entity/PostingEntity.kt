package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity

import java.time.Instant

data class PostingEntity constructor(
    val id: Long? = null,
    val journalId: String,
    val accountCode: String,
    val accountType: String,
    val amount: Long,
    val direction: String, // "DEBIT" or "CREDIT"
    val currency: String,
    val createdAt: Instant
)
