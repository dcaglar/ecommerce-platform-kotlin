package com.dogancaglar.paymentservice.adapter.outbound.persistence.entity

import java.time.LocalDateTime

data class JournalEntryEntity internal  constructor(
    val id: String,
    val txType: String,
    val name: String,
    val referenceType: String?,
    val referenceId: String?,
    val createdAt: LocalDateTime
)