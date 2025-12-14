package com.dogancaglar.paymentservice.domain.model

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderLine
import java.time.LocalDateTime

/**
 * PaymentIntent
 *
 * Represents the shopper's intent to pay.
 * Drives the authorization workflow with the PSP, but does NOT model money movement.
 *
 * Lifecycle (simplified):
 *  CREATED -> PENDING_AUTH -> AUTHORIZED | DECLINED | CANCELLED
 */
class PaymentIntent private constructor(
    val paymentIntentId: PaymentIntentId,
    val buyerId: BuyerId,
    val orderId: OrderId,
    val totalAmount: Amount,
    val paymentOrderLines: List<PaymentOrderLine>,
    val status: PaymentIntentStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {

    init {
        require(paymentOrderLines.isNotEmpty()) { "PaymentIntent must have at least one payment line" }
        require(totalAmount.isPositive()) { "Total amount must be positive" }

        // All lines must share same currency as totalAmount
        val lineCurrencies = paymentOrderLines.map { it.amount.currency }.distinct()
        require(lineCurrencies.size == 1 && lineCurrencies.first() == totalAmount.currency) {
            "All payment lines must use the same currency as total amount"
        }

        // Total amount must equal sum of lines
        val sum = paymentOrderLines.sumOf { it.amount.quantity }
        require(sum == totalAmount.quantity) {
            "Total amount (${totalAmount.quantity}) must equal sum of payment lines ($sum)"
        }
    }

    // ------------------------
    // AUTHORIZATION WORKFLOW
    // ------------------------

    /**
     * Transition from CREATED -> PENDING_AUTH.
     * Indicates that an authorization attempt has been initiated.
     */
    fun markAuthorizedPending(now: LocalDateTime = Utc.nowLocalDateTime()): PaymentIntent {
        require(status == PaymentIntentStatus.CREATED) {
            "Can only start authorization from CREATED (current=$status)"
        }
        return copy(status = PaymentIntentStatus.PENDING_AUTH, updatedAt = now)
    }

    /**
     * Apply a successful authorization result from the PSP.
     * PENDING_AUTH -> AUTHORIZED
     */
    fun markAuthorized(now: LocalDateTime = Utc.nowLocalDateTime()): PaymentIntent {
        require(status == PaymentIntentStatus.PENDING_AUTH) {
            "Can only mark AUTHORIZED from PENDING_AUTH (current=$status)"
        }
        return copy(status = PaymentIntentStatus.AUTHORIZED, updatedAt = now)
    }

    /**
     * Apply a declined authorization result from the PSP.
     * PENDING_AUTH -> DECLINED
     */
    fun markDeclined(now: LocalDateTime = Utc.nowLocalDateTime()): PaymentIntent {
        require(status == PaymentIntentStatus.PENDING_AUTH) {
            "Can only mark DECLINED from PENDING_AUTH (current=$status)"
        }
        return copy(status = PaymentIntentStatus.DECLINED, updatedAt = now)
    }

    /**
     * Cancel the intent before authorization is completed.
     * Allowed from CREATED or PENDING_AUTH.
     */
    fun markCancelled(now: LocalDateTime = Utc.nowLocalDateTime()): PaymentIntent {
        require(
            status == PaymentIntentStatus.CREATED ||
                    status == PaymentIntentStatus.PENDING_AUTH
        ) { "Can only cancel from CREATED or PENDING_AUTH (current=$status)" }

        return copy(status = PaymentIntentStatus.CANCELLED, updatedAt = now)
    }

    // ------------------------
    // INTERNAL COPY
    // ------------------------

    private fun copy(
        status: PaymentIntentStatus = this.status,
        updatedAt: LocalDateTime = Utc.nowLocalDateTime()
    ): PaymentIntent = PaymentIntent(
        paymentIntentId = paymentIntentId,
        buyerId = buyerId,
        orderId = orderId,
        totalAmount = totalAmount,
        paymentOrderLines = paymentOrderLines,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // ------------------------
    // FACTORY METHODS
    // ------------------------

    companion object {
        fun createNew(
            paymentIntentId: PaymentIntentId,
            buyerId: BuyerId,
            orderId: OrderId,
            totalAmount: Amount,
            paymentOrderLines: List<PaymentOrderLine>
        ): PaymentIntent {
            val now = Utc.nowLocalDateTime()
            return PaymentIntent(
                paymentIntentId = paymentIntentId,
                buyerId = buyerId,
                orderId = orderId,
                totalAmount = totalAmount,
                paymentOrderLines = paymentOrderLines,
                status = PaymentIntentStatus.CREATED,
                createdAt = now,
                updatedAt = now
            )
        }


        fun rehydrate(
            paymentIntentId: PaymentIntentId,
            buyerId: BuyerId,
            orderId: OrderId,
            totalAmount: Amount,
            paymentOrderLines: List<PaymentOrderLine>,
            status: PaymentIntentStatus,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): PaymentIntent = PaymentIntent(
            paymentIntentId = paymentIntentId,
            buyerId = buyerId,
            orderId = orderId,
            totalAmount = totalAmount,
            paymentOrderLines = paymentOrderLines,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}