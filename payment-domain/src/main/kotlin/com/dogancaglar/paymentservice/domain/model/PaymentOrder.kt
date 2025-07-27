package com.dogancaglar.paymentservice.domain.model


import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentOrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId
import java.time.LocalDateTime

class PaymentOrder constructor(
    val paymentOrderId: PaymentOrderId,
    val publicPaymentOrderId: String, // Keep as String for display purposes
    val paymentId: PaymentId,
    val publicPaymentId: String, // Keep as String for display purposes
    val sellerId: SellerId,
    val amount: Amount,
    val status: PaymentOrderStatus,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val retryCount: Int = 0,
    val retryReason: String? = null,
    val lastErrorMessage: String? = null
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
            paymentOrderId: PaymentOrderId,
            publicPaymentOrderId: String,
            paymentId: PaymentId,
            publicPaymentId: String,
            sellerId: SellerId,
            amount: Amount,
            createdAt: LocalDateTime,
        ): PaymentOrder {
            return PaymentOrder(
                paymentOrderId = paymentOrderId,
                publicPaymentOrderId = publicPaymentOrderId, // Keep as String
                paymentId = paymentId,
                publicPaymentId = publicPaymentId, // Keep as String
                sellerId = sellerId,
                amount = amount,
                status = PaymentOrderStatus.INITIATED,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }

        fun reconstructFromPersistence(
            paymentOrderId: PaymentOrderId,
            publicPaymentOrderId: String,
            paymentId: PaymentId,
            publicPaymentId: String,
            sellerId: SellerId,
            amount: Amount,
            status: PaymentOrderStatus,
            createdAt: LocalDateTime,
            updatedAt: LocalDateTime,
            retryCount: Int,
            retryReason: String?,
            lastErrorMessage: String?
        ): PaymentOrder = PaymentOrder(
            paymentOrderId = paymentOrderId,
            publicPaymentOrderId = publicPaymentOrderId, // Keep as String
            paymentId = paymentId,
            publicPaymentId = publicPaymentId, // Keep as String
            sellerId = sellerId,
            amount = amount,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            retryCount = retryCount,
            retryReason = retryReason,
            lastErrorMessage = lastErrorMessage
        )
    }

    fun reconstructFromEvent(
        paymentOrderId: PaymentOrderId,
        publicPaymentOrderId: String,
        paymentId: PaymentId,
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
        paymentOrderId = paymentOrderId,
        publicPaymentOrderId = publicPaymentOrderId, // Keep as String
        paymentId = paymentId,
        publicPaymentId = publicPaymentId, // Keep as String
        sellerId = SellerId(sellerId),
        amount = amount,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        retryCount = retryCount,
        retryReason = retryReason,
        lastErrorMessage = lastErrorMessage
    )
}