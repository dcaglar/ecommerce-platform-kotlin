package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import java.time.LocalDateTime

/**
 * Payment
 *
 * Represents the financial transaction created AFTER a PaymentIntent is authorized.
 * Tracks captures and refunds at an aggregate level across all PaymentOrders.
 *
 * Lifecycle (at Payment level):
 *  NOT_CAPTURED -> PARTIALLY_CAPTURED -> CAPTURED
 *  CAPTURED/PARTIALLY_CAPTURED -> PARTIALLY_REFUNDED -> REFUNDED
 */
class Payment private constructor(
    val paymentId: PaymentId,
    val paymentIntentId: PaymentIntentId,
    val buyerId: BuyerId,
    val orderId: OrderId,
    val totalAmount: Amount,
    val capturedAmount: Amount,
    val refundedAmount: Amount,
    val status: PaymentStatus,
    val paymentOrderLines: List<PaymentOrderLine>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {

    init {
        require(totalAmount.isPositive()) { "Total amount must be positive" }
        require(capturedAmount >= Amount.zero(totalAmount.currency)) {
            "Captured amount cannot be negative"
        }
        require(refundedAmount >= Amount.zero(totalAmount.currency)) {
            "Refunded amount cannot be negative"
        }
        require(capturedAmount <= totalAmount) {
            "Captured amount cannot exceed total amount"
        }
        require(refundedAmount <= capturedAmount) {
            "Refunded amount cannot exceed captured amount"
        }

        // Currency consistency
        require(capturedAmount.currency == totalAmount.currency) {
            "Captured amount currency must match total amount"
        }
        require(refundedAmount.currency == totalAmount.currency) {
            "Refunded amount currency must match total amount"
        }

        // PaymentLines invariants (same as PaymentIntent)
        require(paymentOrderLines.isNotEmpty()) { "Payment must have at least one payment line" }
        val lineCurrencies = paymentOrderLines.map { it.amount.currency }.distinct()
        require(lineCurrencies.size == 1 && lineCurrencies.first() == totalAmount.currency) {
            "All payment lines must use the same currency as total amount"
        }
        val sum = paymentOrderLines.sumOf { it.amount.quantity }
        require(sum == totalAmount.quantity) {
            "Total amount (${totalAmount.quantity}) must equal sum of payment lines ($sum)"
        }
    }

    // ------------------------
    // CAPTURE LOGIC
    // ------------------------

    /**
     * Apply a successful capture (sum of one or more PaymentOrders).
     * Updates capturedAmount and PaymentStatus.
     */
    fun applyCapture(
        captureAmount: Amount,
        now: LocalDateTime = Utc.nowLocalDateTime()
    ): Payment {
        require(captureAmount.isPositive()) { "Capture amount must be positive" }
        require(captureAmount.currency == totalAmount.currency) {
            "Capture amount currency must match total amount"
        }
        require(status in setOf(
            PaymentStatus.NOT_CAPTURED,
            PaymentStatus.PARTIALLY_CAPTURED
        )) {
            "Can only capture from NOT_CAPTURED or PARTIALLY_CAPTURED (current=$status)"
        }

        val newCaptured = capturedAmount + captureAmount
        require(newCaptured <= totalAmount) { "Captured amount cannot exceed total amount" }

        val newStatus = when {
            newCaptured == Amount.zero(totalAmount.currency) -> PaymentStatus.NOT_CAPTURED
            newCaptured < totalAmount                        -> PaymentStatus.PARTIALLY_CAPTURED
            newCaptured == totalAmount                       -> PaymentStatus.CAPTURED
            else                                             -> status
        }

        return copy(
            capturedAmount = newCaptured,
            status = newStatus,
            updatedAt = now
        )
    }

    fun voidAuthorization(now: LocalDateTime = Utc.nowLocalDateTime()): Payment {
        require(status == PaymentStatus.NOT_CAPTURED) {
            "Can only void when NOT_CAPTURED (current=$status)"
        }

        return copy(
            status = PaymentStatus.VOIDED,
            updatedAt = now
        )
    }


    // ------------------------
    // REFUND LOGIC
    // ------------------------

    /**
     * Apply a refund (sum of one or more PaymentOrder refunds).
     * Updates refundedAmount and PaymentStatus.
     */
    fun applyRefund(
        refundAmount: Amount,
        now: LocalDateTime = Utc.nowLocalDateTime()
    ): Payment {
        require(refundAmount.isPositive()) { "Refund amount must be positive" }
        require(refundAmount.currency == totalAmount.currency) {
            "Refund amount currency must match total amount"
        }
        require(capturedAmount > Amount.zero(totalAmount.currency)) {
            "Cannot refund a payment with zero captured amount"
        }
        require(status in setOf(
            PaymentStatus.PARTIALLY_CAPTURED,
            PaymentStatus.CAPTURED,
            PaymentStatus.PARTIALLY_REFUNDED
        )) {
            "Can only refund from CAPTURED / PARTIALLY_CAPTURED / PARTIALLY_REFUNDED (current=$status)"
        }

        val newRefunded = refundedAmount + refundAmount
        require(newRefunded <= capturedAmount) {
            "Refunded amount ($newRefunded) cannot exceed captured amount ($capturedAmount)"
        }

        val newStatus = when {
            newRefunded == Amount.zero(totalAmount.currency) -> status // should not happen with positive refund
            newRefunded < capturedAmount                     -> PaymentStatus.PARTIALLY_REFUNDED
            newRefunded == capturedAmount                    -> PaymentStatus.REFUNDED
            else                                             -> status
        }

        return copy(
            refundedAmount = newRefunded,
            status = newStatus,
            updatedAt = now
        )
    }

    // ------------------------
    // INTERNAL COPY
    // ------------------------

    private fun copy(
        capturedAmount: Amount = this.capturedAmount,
        refundedAmount: Amount = this.refundedAmount,
        status: PaymentStatus = this.status,
        updatedAt: LocalDateTime = Utc.nowLocalDateTime()
    ): Payment = Payment(
        paymentId = paymentId,
        paymentIntentId = paymentIntentId,
        buyerId = buyerId,
        orderId = orderId,
        totalAmount = totalAmount,
        capturedAmount = capturedAmount,
        refundedAmount = refundedAmount,
        status = status,
        paymentOrderLines = paymentOrderLines,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // ------------------------
    // FACTORY METHODS
    // ------------------------

    companion object {

        /**
         * Create a Payment AFTER a PaymentIntent has been AUTHORIZED.
         * Typically called from the application layer when handling PaymentIntentAuthorized.
         */
        fun fromAuthorizedIntent(
            paymentId: PaymentId,
            intent: PaymentIntent
        ): Payment {
            require(intent.status == PaymentIntentStatus.AUTHORIZED) {
                "Cannot create Payment from non-AUTHORIZED PaymentIntent (current=${intent.status})"
            }

            val zero = Amount.zero(intent.totalAmount.currency)
            val now = Utc.nowLocalDateTime()
            
            return Payment(
                paymentId = paymentId,
                paymentIntentId = intent.paymentIntentId,
                buyerId = intent.buyerId,
                orderId = intent.orderId,
                totalAmount = intent.totalAmount,
                capturedAmount = zero,
                refundedAmount = zero,
                status = PaymentStatus.NOT_CAPTURED,
                paymentOrderLines = intent.paymentOrderLines,
                createdAt = now,
                updatedAt = now
            )
        }

        fun rehydrate(
            paymentId: PaymentId,
            paymentIntentId: PaymentIntentId,
            buyerId: BuyerId,
            orderId: OrderId,
            totalAmount: Amount,
            capturedAmount: Amount,
            refundedAmount: Amount,
            status: PaymentStatus,
            paymentOrderLines: List<PaymentOrderLine>,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): Payment = Payment(
            paymentId = paymentId,
            paymentIntentId = paymentIntentId,
            buyerId = buyerId,
            orderId = orderId,
            totalAmount = totalAmount,
            capturedAmount = capturedAmount,
            refundedAmount = refundedAmount,
            status = status,
            paymentOrderLines = paymentOrderLines,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
