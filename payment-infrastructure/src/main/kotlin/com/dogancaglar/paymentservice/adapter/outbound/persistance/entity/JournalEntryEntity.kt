package com.dogancaglar.paymentservice.adapter.outbound.persistance.entity

import java.time.LocalDateTime

data class JournalEntryEntity(
    val id: String,
    val txType: String,
    val name: String,
    val referenceType: String?,
    val referenceId: String?,
    val createdAt: LocalDateTime
)