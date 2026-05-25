package com.dogancaglar.paymentservice.domain.model.payment

import com.dogancaglar.common.time.Utc
import java.time.LocalDateTime

class PaymentOrderStatusCheck private constructor(
    val id: Long? = null,
    val paymentOrderId: Long,
    val scheduledAt: LocalDateTime,
    val attempt: Int = 1,
    val status: Status = Status.SCHEDULED,
    val createdAt: LocalDateTime = Utc.nowLocalDateTime(),
    val updatedAt: LocalDateTime = Utc.nowLocalDateTime(),
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

    override fun toString(): String {
        return "PaymentOrderStatusCheck(id=$id, paymentOrderId=$paymentOrderId, scheduledAt=$scheduledAt, attempt=$attempt, status=$status, createdAt=$createdAt, updatedAt=$updatedAt)"
    }

    companion object {
        fun createNew(paymentOrderId: Long, scheduledAt: LocalDateTime): PaymentOrderStatusCheck =
            PaymentOrderStatusCheck(
                paymentOrderId = paymentOrderId,
                scheduledAt = scheduledAt,
                attempt = 1,
                status = Status.SCHEDULED,
                createdAt = Utc.nowLocalDateTime(),
                updatedAt = Utc.nowLocalDateTime()
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