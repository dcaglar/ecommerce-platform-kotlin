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
    val clientSecret : String?="",
    val pspReference: String?,          // Stripe PaymentIntent id (nullable only before CREATED)
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

        // Domain invariants about PSP reference:
        when (status) {
            PaymentIntentStatus.CREATED_PENDING -> {
                require(pspReference == null) {
                    "pspReference must be null in CREATED_PENDING"
                }
            }
            PaymentIntentStatus.CREATED,
            PaymentIntentStatus.PENDING_AUTH,
            PaymentIntentStatus.AUTHORIZED,
            PaymentIntentStatus.DECLINED,
             ->{
                require(!pspReference.isNullOrBlank()) {
                    "pspReference is required in status=$status"
                }
            }

            else -> {}
        }
    }

    fun hasPspReference(): Boolean = !pspReference.isNullOrBlank()

    fun pspReferenceOrThrow(): String =
        requireNotNull(pspReference) { "pspReference is not set for paymentIntentId=$paymentIntentId" }

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


    fun markAsCreated(now: LocalDateTime = Utc.nowLocalDateTime()): PaymentIntent {
        require(status == PaymentIntentStatus.CREATED_PENDING) {
            "Can only start authorization from CREATED (current=$status)"
        }
        return copy(status = PaymentIntentStatus.CREATED, updatedAt = now)
    }


    /**
     *      * CREATED_PENDING -> CREATED (must provide PSP reference,client secret,seecret never persisted)
     */
    fun markAsCreatedWithPspReferenceAndClientSecret(pspReference: String, clientSecret: String, now: LocalDateTime = Utc.nowLocalDateTime()): PaymentIntent {
        require(status == PaymentIntentStatus.CREATED_PENDING) {
            "Can only mark CREATED from CREATED_PENDING (current=$status)"
        }
        require(pspReference.isNotBlank()) { "pspReference must not be blank" }
        // Note: clientSecret is only set in-memory for response, never persisted
        return copy(status = PaymentIntentStatus.CREATED, updatedAt = now, pspReference = pspReference, clientSecret = clientSecret)
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

    /**
     * Update clientSecret (used when retrieving from Stripe during polling)
     * Preserves existing pspReference (must already be set for statuses that require it)
     */
    fun withClientSecret(clientSecret: String): PaymentIntent {
        return copy(pspReference = this.pspReference, clientSecret = clientSecret)
    }

    // ------------------------
    // INTERNAL COPY
    // ------------------------

    private fun copy(
        status: PaymentIntentStatus = this.status,
        updatedAt: LocalDateTime = Utc.nowLocalDateTime(),
        pspReference: String? = this.pspReference,
        clientSecret: String? = this.clientSecret,
    ): PaymentIntent = PaymentIntent(
        paymentIntentId = paymentIntentId,
        pspReference = pspReference,
        clientSecret = clientSecret,
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
                pspReference = null,
                buyerId = buyerId,
                orderId = orderId,
                totalAmount = totalAmount,
                paymentOrderLines = paymentOrderLines,
                status = PaymentIntentStatus.CREATED_PENDING,
                createdAt = now,
                updatedAt = now
            )
        }


        fun rehydrate(
            paymentIntentId: PaymentIntentId,
            pspReference: String?="",
            buyerId: BuyerId,
            orderId: OrderId,
            totalAmount: Amount,
            paymentOrderLines: List<PaymentOrderLine>,
            status: PaymentIntentStatus,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime
        ): PaymentIntent = PaymentIntent(
            paymentIntentId = paymentIntentId,
            pspReference=pspReference,
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