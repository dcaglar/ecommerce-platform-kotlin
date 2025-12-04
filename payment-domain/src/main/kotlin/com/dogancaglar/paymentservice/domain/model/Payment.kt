package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.vo.*
import java.time.LocalDateTime

class Payment private constructor(
    val paymentId: PaymentId,
    val buyerId: BuyerId,
    val orderId: OrderId,
    val totalAmount: Amount,
    val capturedAmount: Amount,
    val status: PaymentStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val paymentLines: List<PaymentLine>    // ← stored as JSONB in Persistence
) {

    // ------------------------
    // AUTHORIZATION FLOW
    // ------------------------

    fun startAuthorization(now: LocalDateTime? = Utc.nowLocalDateTime()): Payment {
        require(status == PaymentStatus.CREATED) {
            "Can only start authorization from CREATED"
        }
        return copy(status = PaymentStatus.PENDING_AUTH, updatedAt = now!!)
    }

    fun authorize(now: LocalDateTime = Utc.nowLocalDateTime()): Payment {
        require(status == PaymentStatus.PENDING_AUTH) {
            "Payment can only be authorized from PENDING_AUTH"
        }
        return copy(status = PaymentStatus.AUTHORIZED, updatedAt = now)
    }

    fun decline(now: LocalDateTime = Utc.nowLocalDateTime()): Payment {
        require(status == PaymentStatus.PENDING_AUTH) {
            "Payment can only be declined from PENDING_AUTH"
        }
        return copy(status = PaymentStatus.DECLINED, updatedAt = now)
    }


    // ------------------------
    // CAPTURE FLOW
    // ------------------------

    fun addCapturedAmount(amount: Amount): Payment {
        val newCaptured = capturedAmount + amount
        require(newCaptured <= totalAmount) { "Captured amount cannot exceed total" }

        val newStatus = when {
            newCaptured == totalAmount -> PaymentStatus.CAPTURED
            newCaptured < totalAmount -> PaymentStatus.PARTIALLY_CAPTURED
            else -> status
        }

        return copy(
            capturedAmount = newCaptured,
            status = newStatus
        )
    }


    // ------------------------
    // COPY METHOD
    // ------------------------

    private fun copy(
        status: PaymentStatus = this.status,
        capturedAmount: Amount = this.capturedAmount,
        updatedAt: LocalDateTime = Utc.nowLocalDateTime(),
        paymentLines: List<PaymentLine> = this.paymentLines
    ): Payment = Payment(
        paymentId = paymentId,
        buyerId = buyerId,
        orderId = orderId,
        totalAmount = totalAmount,
        capturedAmount = capturedAmount,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        paymentLines = paymentLines
    )


    // ------------------------
    // FACTORY METHODS
    // ------------------------

    companion object {

        fun createNew(
            paymentId: PaymentId,
            buyerId: BuyerId,
            orderId: OrderId,
            totalAmount: Amount,
            paymentLines: List<PaymentLine>
        ): Payment {
            require(paymentLines.isNotEmpty()) { "Payment must have at least one payment line" }
            require(totalAmount.isPositive()) { "Total amount must be positive" }

            val now = Utc.nowLocalDateTime()

            return Payment(
                paymentId = paymentId,
                buyerId = buyerId,
                orderId = orderId,
                totalAmount = totalAmount,
                capturedAmount = Amount.zero(totalAmount.currency),
                status = PaymentStatus.CREATED,    // ← Important change!
                createdAt = now,
                updatedAt = now,
                paymentLines = paymentLines
            )
        }

        fun rehydrate(
            paymentId: PaymentId,
            buyerId: BuyerId,
            orderId: OrderId,
            totalAmount: Amount,
            capturedAmount: Amount,
            status: PaymentStatus,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
            paymentLines: List<PaymentLine>
        ): Payment = Payment(
            paymentId,
            buyerId,
            orderId,
            totalAmount,
            capturedAmount,
            status,
            createdAt,
            updatedAt,
            paymentLines
        )
    }
}