package com.dogancaglar.payment.domain.model

import java.time.LocalDateTime

class PaymentOrderStatusCheck private constructor(
    val id: Long? = null,
    val paymentOrderId: Long,
    val scheduledAt: LocalDateTime,
    val attempt: Int = 1,
    val status: Status = Status.SCHEDULED,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
) {

    enum class Status { SCHEDULED, PROCESSED }

    fun markProcessed(now: LocalDateTime) = copy(
        status = Status.PROCESSED,
        updatedAt = now
    )

    fun reschedule(nextScheduledAt: LocalDateTime, now: LocalDateTime) = copy(
        attempt = this.attempt + 1,
        scheduledAt = nextScheduledAt,
        updatedAt = now
    )

    private fun copy(
        id: Long? = this.id,
        paymentOrderId: Long = this.paymentOrderId,
        scheduledAt: LocalDateTime = this.scheduledAt,
        attempt: Int = this.attempt,
        status: Status = this.status,
        createdAt: LocalDateTime = this.createdAt,
        updatedAt: LocalDateTime = this.updatedAt
    ): PaymentOrderStatusCheck = PaymentOrderStatusCheck(
        id, paymentOrderId, scheduledAt, attempt, status, createdAt, updatedAt
    )

    companion object {
        fun createNew(paymentOrderId: Long, scheduledAt: LocalDateTime): PaymentOrderStatusCheck =
            PaymentOrderStatusCheck(
                paymentOrderId = paymentOrderId,
                scheduledAt = scheduledAt,
                attempt = 1,
                status = Status.SCHEDULED,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

        fun reconstructFromPersistence(
            id: Long,
            paymentOrderId: Long,
            scheduledAt: LocalDateTime,
            attempt: Int,
            status: Status,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): PaymentOrderStatusCheck =
            PaymentOrderStatusCheck(id, paymentOrderId, scheduledAt, attempt, status, createdAt, updatedAt)
    }
}