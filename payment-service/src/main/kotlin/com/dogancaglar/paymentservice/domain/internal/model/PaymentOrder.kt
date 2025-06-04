package com.dogancaglar.paymentservice.domain.internal.model

import com.dogancaglar.paymentservice.domain.model.Amount
import com.dogancaglar.paymentservice.domain.model.PaymentOrderStatus
import java.time.LocalDateTime

class PaymentOrder private constructor(
    val paymentOrderId: Long,
    val publicPaymentOrderId: String,
    val paymentId: Long,
    val publicPaymentId: String,
    val sellerId: String,
    val amount: Amount,
    val status: PaymentOrderStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val retryCount: Int = 0,
    val retryReason: String?=null,
    val lastErrorMessage: String?=null
) {

    fun markAsFailed() = copy(status = PaymentOrderStatus.FAILED)
    fun markAsPaid() = copy(status = PaymentOrderStatus.SUCCESSFUL)
    fun markAsPending() = copy(status = PaymentOrderStatus.PENDING)
    fun markAsFinalizedFailed() = copy(status = PaymentOrderStatus.FINALIZED_FAILED)
    fun incrementRetry() = copy(retryCount = retryCount + 1)
    fun withRetryReason(reason: String?) = copy(retryReason = reason)
    fun withLastError(error: String?) = copy(lastErrorMessage = error)
    fun withUpdatedAt(now: LocalDateTime) = copy(updatedAt = now)
    // ⚠️ We reimplement 'copy' ourselves because it's no longer a data class
    private fun copy(
        status: PaymentOrderStatus = this.status,
        retryCount: Int = this.retryCount,
        retryReason: String? = this.retryReason,
        lastErrorMessage: String? = this.lastErrorMessage,
        updatedAt: LocalDateTime = this.updatedAt
    ): PaymentOrder = PaymentOrder(
        paymentOrderId = this.paymentOrderId,
        publicPaymentOrderId = this.publicPaymentOrderId,
        paymentId = this.paymentId,
        publicPaymentId = this.publicPaymentId,
        sellerId = this.sellerId,
        amount = this.amount,
        status = status,
        createdAt = this.createdAt,
        updatedAt = updatedAt,
        retryCount = retryCount,
        retryReason = retryReason,
        lastErrorMessage = lastErrorMessage
    )

    companion object {
        fun createNew(
            paymentOrderId: Long,
            publicPaymentOrderId: String,
            paymentId: Long,
            publicPaymentId: String,
            sellerId: String,
            amount: Amount,
            createdAt: LocalDateTime,
        ): PaymentOrder {
            return PaymentOrder(
                paymentOrderId= paymentOrderId,
                publicPaymentOrderId =publicPaymentOrderId,
                paymentId =paymentId,
                publicPaymentId = publicPaymentId,
                sellerId =sellerId,
                amount = amount,
                status= PaymentOrderStatus.INITIATED,
                createdAt = createdAt,
                updatedAt =createdAt
            )
        }

        fun reconstructFromPersistence(
            paymentOrderId: Long,
            publicPaymentOrderId: String,
            paymentId: Long,
            publicPaymentId: String,
            sellerId: String,
            amount: Amount,
            status: PaymentOrderStatus,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
            retryCount: Int,
            retryReason: String?,
            lastErrorMessage: String?
        ): PaymentOrder = PaymentOrder(
            paymentOrderId,
            publicPaymentOrderId,
            paymentId,
            publicPaymentId,
            sellerId,
            amount,
            status,
            createdAt,
            updatedAt,
            retryCount,
            retryReason,
            lastErrorMessage
        )
    }

    fun reconstructFromEvent(
        paymentOrderId: Long,
        publicPaymentOrderId: String,
        paymentId: Long,
        publicPaymentId: String,
        sellerId: String,
        amount: Amount,
        status: PaymentOrderStatus,
        createdAt: LocalDateTime,
        updatedAt: LocalDateTime,
        retryCount: Int = 0,
        retryReason: String? = null,
        lastErrorMessage: String? = null
    ): PaymentOrder = PaymentOrder(
        paymentOrderId,
        publicPaymentOrderId,
        paymentId,
        publicPaymentId,
        sellerId,
        amount,
        status,
        createdAt,
        updatedAt,
        retryCount,
        retryReason,
        lastErrorMessage
    )
}