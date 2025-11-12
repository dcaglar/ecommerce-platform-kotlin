package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import java.time.Clock
import java.time.LocalDateTime
import kotlin.collections.plus


class Payment private constructor(
    val paymentId: PaymentId,
    val buyerId: BuyerId,
    val orderId: OrderId,
    val totalAmount: Amount,
    val capturedAmount: Amount,
    val status: PaymentStatus,
    val idempotencyKey: String,            // <-- internal business idempotency
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val paymentOrders: List<PaymentOrder>
) {

    val publicPaymentId: String
        get() = "payment-${paymentId.value}"

    // --- Domain Behavior ---

    fun authorize(updatedAt: LocalDateTime= LocalDateTime.now(Clock.systemUTC())): Payment {
        require(status == PaymentStatus.PENDING_AUTH) { "Payment can only be authorized from PENDING_AUTH" }
        return copy(status = PaymentStatus.AUTHORIZED, updatedAt = updatedAt )
    }


    fun decline(updatedAt: LocalDateTime= LocalDateTime.now(Clock.systemUTC())): Payment {
        require(status == PaymentStatus.PENDING_AUTH) { "Payment can only be declined from PENDING_AUTH" }
        return copy(status = PaymentStatus.DECLINED, updatedAt = updatedAt)
    }


    fun addCapturedAmount(amount: Amount): Payment {
        val newCaptured = this.capturedAmount + amount
        require(newCaptured <= totalAmount) { "Captured amount cannot exceed total" }

        val newStatus = when {
            newCaptured == totalAmount -> PaymentStatus.CAPTURED
            newCaptured < totalAmount -> PaymentStatus.CAPTURED_PARTIALLY
            else -> status
        }

        return copy(capturedAmount = newCaptured, status = newStatus,updatedAt=updatedAt)
    }

    fun addPaymentOrder(paymentOrder: PaymentOrder): Payment {
        require(paymentOrder.paymentId == paymentId) {
            "PaymentOrder must reference the same Payment"
        }
        require(paymentOrder.amount.currency == totalAmount.currency) {
            "Currency mismatch between Payment and PaymentOrder"
        }
        return copy(paymentOrders = paymentOrders + paymentOrder, updatedAt = updatedAt)
    }


    // --- Internal copy (immutability) ---
    private fun  copy(
        capturedAmount: Amount = this.capturedAmount,
        status: PaymentStatus = this.status,
        paymentOrders: List<PaymentOrder> = this.paymentOrders,
        updatedAt: LocalDateTime
    ): Payment = Payment(
        paymentId = paymentId,
        buyerId = buyerId,
        orderId = orderId,
        totalAmount = totalAmount,
        capturedAmount = capturedAmount,
        status = status,
        idempotencyKey = idempotencyKey,
        createdAt = createdAt,
        updatedAt = updatedAt,
        paymentOrders = paymentOrders
    )

    companion object {
        fun createNew(
            paymentId: PaymentId,
            buyerId: BuyerId,
            orderId: OrderId,
            totalAmount: Amount,
            clock: Clock = Clock.systemUTC()
        ): Payment {
            require(totalAmount.isPositive()) { "Total amount must be positive" }
            val idempotencyKeyGenerated = "${buyerId.value}:${orderId.value}:${totalAmount.quantity}:${totalAmount.currency.currencyCode}"
            return Payment(
                paymentId = paymentId,
                buyerId = buyerId,
                orderId = orderId,
                totalAmount = totalAmount,
                capturedAmount = Amount.zero(totalAmount.currency),
                status = PaymentStatus.PENDING_AUTH,
                idempotencyKey = idempotencyKeyGenerated,
                createdAt = LocalDateTime.now(clock),
                updatedAt = LocalDateTime.now(clock),
                paymentOrders = emptyList()
            )

        }

        fun rehydrate(
            paymentId: PaymentId,
            buyerId: BuyerId,
            orderId: OrderId,
            totalAmount: Amount,
            capturedAmount: Amount,
            status: PaymentStatus,
            idempotencyKey: String,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): Payment = Payment(
            paymentId,
            buyerId,
            orderId,
            totalAmount,
            capturedAmount,
            status,
            idempotencyKey,
            createdAt,
            updatedAt,
            emptyList()
        )
    }

}