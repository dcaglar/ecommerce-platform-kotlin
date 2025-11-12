package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.LocalDateTime

data class PostingEntity internal constructor(
    val id: Long? = null,
    val journalId: String,
    val accountCode: String,
    val accountType: String,
    val amount: Long,
    val direction: String, // "DEBIT" or "CREDIT"
    val currency: String,
    val createdAt: LocalDateTime
)